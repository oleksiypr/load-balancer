package op.op.assignment.iptiq

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.Behaviors.{receiveMessage, setup}
import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import op.op.assignment.iptiq.balancer.{HeartBeat, LoadBalancer}
import op.op.assignment.iptiq.balancer.LoadBalancer.Register
import op.op.assignment.iptiq.provider.Provider

object Main extends App {

  sealed trait Message
  case object Stop extends Message
  case object Request extends Message

  val requester: Behavior[String] = Behaviors.receiveMessage(_ => Behaviors.same)

  val main: Behavior[Message] =  setup { ctx =>
    val replyTo = ctx.spawnAnonymous(requester)
    val max = 10
    val providers = (0 until max)
      .map(i => ctx
        .spawn(
          Provider.provider(s"#$i"),
          name = s"provider-$i"
        )
      ).toVector

    val balancer  = ctx.spawn(
      LoadBalancer.idle(max, heartBeatFactory = HeartBeat.default),
      name = "balancer"
    )

    balancer ! Register(providers)

    receiveMessage {

      case Request =>
        balancer ! LoadBalancer.Request(replyTo)
        Behaviors.same

      case Stop => stop
    }
  }

  val stop = Behaviors.receiveSignal[Message] {
    case (_, Terminated(_)) =>
      Behaviors.stopped
  }

  val system: ActorSystem[Message] = ActorSystem(main, "load-balancer")
  Thread.sleep(2000)

  val n = 100
  for (_ <- 0 to 100) {
    system ! Request
  }

  Thread.sleep(2000)
  system ! Stop
  system.terminate()
}
