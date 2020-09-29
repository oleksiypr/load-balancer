package op.op.assignment.iptiq

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}


object LoadBalancer {

  sealed trait Message
  sealed trait Query extends Message
  sealed trait Command extends Message
  final case class Register(providers: Vector[ActorRef[Query]]) extends Query
  final case class Get(replyTo: ActorRef[String]) extends Query

  def balancer(providers: Vector[ActorRef[Query]]): Behavior[Message] =
    Behaviors.setup[Message] { ctx =>
      Behaviors.receiveMessage[Message] {
        case Register(ps) => balancer(providers ++ ps)
        case req @ Get(_) =>
          providers(0) ! req
          Behaviors.same
      }
    }
}
