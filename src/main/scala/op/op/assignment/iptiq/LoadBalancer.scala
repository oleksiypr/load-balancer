package op.op.assignment.iptiq

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}


object LoadBalancer {

  sealed trait Message
  sealed trait Query extends Message
  sealed trait Command extends Message
  final case class Register(providers: Vector[ActorRef[Query]]) extends Command
  final case class Get(replyTo: ActorRef[String]) extends Query
  final case class Response(id: String, requester: ActorRef[String]) extends Message

  type BalanceStrategy = Int => Int

  def roundRobin(n: Int)(i: Int): Int =  (i + 1) % n

  def balancer(
    providers: Vector[ActorRef[Query]]
  )(current: Int = 0,
    next: BalanceStrategy = roundRobin(providers.size)
  ): Behavior[Message] =
    Behaviors.setup[Message] { ctx =>
      Behaviors.receiveMessage[Message] {
        case Register(ps) =>
          val updated = providers ++ ps
          balancer(updated)(current = 0)
        case req @ Get(_) =>
          providers(current) ! req
          balancer(providers)(next(current))
        case Response(id, requester) =>
          requester ! id
          Behaviors.same
      }
    }
}
