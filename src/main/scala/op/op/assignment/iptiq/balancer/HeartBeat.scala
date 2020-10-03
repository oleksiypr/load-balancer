package op.op.assignment.iptiq.balancer

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

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
