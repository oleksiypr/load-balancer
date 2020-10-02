package op.op.assignment.iptiq

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import op.op.assignment.iptiq.LoadBalancer.Message

object LoadBalancer {

  sealed trait Message
  sealed trait Query extends Message
  sealed trait Command extends Message
  final case class Register(providers: Vector[ActorRef[Query]]) extends Command
  final case class Get(replyTo: ActorRef[String]) extends Query
  final case class Response(id: String, requester: ActorRef[String]) extends Message

  type BalanceStrategy = Int => Int

  def roundRobin(n: Int)(i: Int): Int =  (i + 1) % n

  def idle(max: Int): Behavior[Message] =
    Behaviors.setup[Message] { ctx =>
      Behaviors.receiveMessage[Message] {
        case Register(providers) =>
          val ps = providers.take(max)
          ps.foreach(_ => ctx.spawnAnonymous(HeartBeat.checker(ctx.self)))
          balancer(providers.take(max))(current = 0)
        case _ =>
          Behaviors.same
      }
    }

  def balancer(
    providers: Vector[ActorRef[Query]]
  )(current: Int = 0,
    next: BalanceStrategy = roundRobin(providers.size)
  ): Behavior[Message] =
    Behaviors.setup[Message] { ctx =>
      Behaviors.receiveMessage[Message] {
        case req @ Get(_) =>
          providers(current) ! req
          balancer(providers)(next(current))
        case Response(id, requester) =>
          requester ! id
          Behaviors.same
        case Register(_) =>
          Behaviors.same
      }
    }
}

object HeartBeat {

  sealed trait Message
  case object Check extends Message

  def checker(
    balancer: ActorRef[LoadBalancer.Message]
  ): Behavior[Message] =
    Behaviors.setup[Message] { _ =>
      Behaviors.receiveMessage[Message](_ => Behaviors.same)
    }

}
