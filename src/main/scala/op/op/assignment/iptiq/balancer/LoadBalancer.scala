package op.op.assignment.iptiq.balancer

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import op.op.assignment.iptiq.provider.Provider

object LoadBalancer {

  import Behaviors.{setup, receiveMessage}

  type SelfRef     = ActorRef[Message]
  type ProviderRef = Provider.SelfRef

  type Max    = Int
  type Index  = Int
  type Next   = Int

  type HeartBeatFactory = (Index, SelfRef, ProviderRef) => Behavior[HeartBeat.Message]
  type BalanceStrategy  = Max => Index => Next

  val noHeartBeat: HeartBeatFactory = (_, _, _) => Behaviors.ignore

  def roundRobin(n: Int)(i: Int): Int = (i + 1) % n

  sealed trait Message
  final case class Register(providerRefs: Vector[ProviderRef]) extends Message
  final case class Request(requester: ActorRef[String]) extends Message
  final case class Response(id: String, requester: ActorRef[String]) extends Message
  final case class ProviderUp(index: Int) extends Message
  final case class ProviderDown(index: Int) extends Message
  final case class Exclude(index: Int) extends Message
  final case class Include(index: Int) extends Message

  def idle(
    max: Int,
    strategy: BalanceStrategy = roundRobin,
    heartBeatFactory: HeartBeatFactory = noHeartBeat
  ): Behavior[Message] = setup { ctx => receiveMessage {

      case Register(providerRefs) =>
        val refs = providerRefs.take(max)
        val providers = State(refs.map(ProviderState(_, Unavailable)))
        refs.zipWithIndex.foreach {
          case (ref, index) =>
            ctx.spawn(
              heartBeatFactory(index, ctx.self, ref),
              name = s"provider-$index"
            )
        }
        balancer(providers, strategy)(current = 0)

      case _ =>
        Behaviors.same
    }
  }

  def balancer(
    providers: State = State(Vector.empty),
    strategy: BalanceStrategy
  )(
    current: Int,
    next: Index => Next = strategy(providers.size)
  ): Behavior[Message] = setup { ctx => receiveMessage {

      case Request(requester) =>
        providers(current) match {
          case Some(p) =>
            ctx.log.info(s"Request will be dispatched to ${p.providerRef}")
            p.providerRef ! Provider.Get(requester, ctx.self)
            balancer(providers, strategy)(next(current))
          case None    =>
            requester ! "No providers available"
            Behaviors.same
        }

      case Response(id, requester) =>
        ctx.log.info(s"Response: {$id} received")
        requester ! id
        Behaviors.same

      case ProviderUp(index) =>
        ctx.log.warn(s"Provider[$index] down")
        balancer(providers.up(index), strategy)(current)

      case ProviderDown(index) =>
        ctx.log.debug(s"Provider[$index] up")
        balancer(providers.down(index), strategy)(current)

      case Exclude(index) =>
        ctx.log.debug(s"Provider[$index] excluded")
        balancer(providers.exclude(index), strategy)(current)

      case Include(index) =>
        ctx.log.debug(s"Provider[$index] included")
        balancer(providers.include(index), strategy)(current)

      case Register(_) => Behaviors.same
    }
  }
}
