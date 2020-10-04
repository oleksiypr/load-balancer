package op.op.assignment.iptiq.balancer

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

/**
 * Here we emulate request to real provider.
 */
object ProviderProxy {

  type SelfRef = ActorRef[Message]

  sealed trait Message
  final case class Get(requester: ActorRef[String], balancer: LoadBalancer.SelfRef) extends Message
  final case class Check(replyTo: HeartBeat.SelfRef, acknowledge: HeartBeat.Message) extends Message

  private[this] val rnd       = scala.util.Random
  private[this] val threshold = 0.5

  def available(): Boolean = rnd.nextDouble() >= threshold

  def doRequest(): Unit = ()

  def doCheck(): Unit = ()

  def provider(id: String): Behavior[Message] = Behaviors.receiveMessage {
    case Get(requester, balancer) =>
      doRequest()
      balancer ! LoadBalancer.Response(id, requester)
      Behaviors.same

    case Check(replyTo, ack) if available() =>
      doCheck()
      replyTo ! ack
      Behaviors.same

    case Check(_, _) => Behaviors.same
  }
}

