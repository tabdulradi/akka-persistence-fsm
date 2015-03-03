package sample.persistence

import akka.actor.FSM.Event

import scala.concurrent.duration._

import akka.actor._
import akka.persistence._


object ViewExample extends App {
  trait State
  object State {
    def fromCount(count: Int): State = {
      if (count < 5) LessThan5
      else if (count < 10) LessThan10
      else MoreThan10
    }
  }
  case object LessThan5 extends State
  case object LessThan10 extends State
  case object MoreThan10 extends State

  case class Data(count: Int = 0)

  class ExamplePersistentActor extends PersistentActor {
    override def persistenceId = "sample-id-5"

    var state = Data()

    def receiveCommand: Actor.Receive = {
      case "print" =>
        println(s"persistentActor has: ${state.count}" )
      case "snap" =>
        println(s"persistentActor saved Snapshot")
        saveSnapshot(state)
      case payload: String =>
        println(s"persistentActor received ${payload} (nr = ${state.count})")
        persist(payload) { evt =>
          state = state.copy(count = state.count + 1)
        }
    }

    def receiveRecover: Actor.Receive = {
      case SnapshotOffer(_, snapshot: Data) =>
        println(s"persistentActor received snapshot offer ${snapshot}")
        state = snapshot
      case msg: String =>
        println(s"persistentActor[receiveRecover] received ${msg} (nr = ${state.count})")
        state = state.copy(count = state.count + 1)
    }
  }

  class ExampleView(name: String) extends PersistentView with FSM[State, Data] {
    override def persistenceId: String = "sample-id-5"
    override def viewId = "sample-view-id-5-" + name

    startWith(LessThan5, Data())

    when(LessThan5){
      case Event(SnapshotOffer(_, snapshot @ Data(newCount)), Data(numReplicated)) =>
        println(s"view($name)[$numReplicated<5] received SnapshotOffer with $newCount (isPersistent = $isPersistent)")
        if (newCount < 5) stay using snapshot
        else if (newCount < 10) goto(LessThan10) using snapshot
        else goto(MoreThan10) using snapshot
      case Event("increment", Data(4)) =>
        println(s"view($name)[4] received persistent increment event (isPersistent = $isPersistent)")
        goto(LessThan10) using Data(5)
      case Event("increment", Data(numReplicated)) =>
        println(s"view($name)[$numReplicated<5] received persistent increment event (isPersistent = $isPersistent)")
        stay using Data(numReplicated + 1)
    }
    when(LessThan10){
      case Event("increment", Data(9)) =>
        println(s"view($name)[9] received persistent increment event (isPersistent = $isPersistent)")
        goto(MoreThan10) using Data(10)
      case Event("increment", Data(numReplicated)) =>
        println(s"view($name)[$numReplicated<10] received persistent increment event (isPersistent = $isPersistent)")
        stay using Data(numReplicated + 1)
    }
    when(MoreThan10){
      case Event("increment", Data(numReplicated)) =>
        println(s"view($name)[$numReplicated>=10] received persistent increment event (isPersistent = $isPersistent)")
        stay using Data(numReplicated + 1)
    }
    whenUnhandled {
      case Event("print", Data(numReplicated)) =>
        println(s"view($name)[$stateName using $stateData] (isPersistent = $isPersistent)" )
        stay
      case Event("snap", data) =>
        println(s"view($name) saved Snapshot (isPersistent = $isPersistent)")
        saveSnapshot(data)
        stay
      case Event(other, data) =>
        println(s"view($name)[$stateName using $stateData] got $other (isPersistent = $isPersistent)")
        stay
    }

    onTransition {
      case a -> b =>
        println(s"view($name) went from $a to $b with old data: $stateData and new data: $nextStateData <<<<<< isPersistent = $isPersistent")
    }

  }

  val system = ActorSystem("example")

  val persistentActor = system.actorOf(Props(classOf[ExamplePersistentActor]))
  val view = system.actorOf(Props(classOf[ExampleView], "1"))

  view ! "print"
  persistentActor ! "print"
  scala.io.StdIn.readLine()
  persistentActor ! "increment"
  persistentActor ! "increment"
  scala.io.StdIn.readLine()
  view ! "print"
  persistentActor ! "print"
  scala.io.StdIn.readLine()
  view ! "snap"
  scala.io.StdIn.readLine()
  view ! "print"
  persistentActor ! "print"
  scala.io.StdIn.readLine()
  persistentActor ! "increment"
  persistentActor ! "increment"
  persistentActor ! "snap"
  persistentActor ! "increment"
  persistentActor ! "increment"
  persistentActor ! "snap"
  scala.io.StdIn.readLine()
  view ! "print"
  persistentActor ! "print"
  scala.io.StdIn.readLine()
  view ! "snap"
  scala.io.StdIn.readLine()
  view ! "print"
  persistentActor ! "print"
  scala.io.StdIn.readLine()
  persistentActor ! "increment"
  persistentActor ! "increment"
  persistentActor ! "increment"
  persistentActor ! "increment"
  persistentActor ! "increment"
  persistentActor ! "increment"
  persistentActor ! "increment"
  persistentActor ! "increment"
  scala.io.StdIn.readLine()
  view ! "print"
  persistentActor ! "print"
  scala.io.StdIn.readLine()


  val view2 = system.actorOf(Props(classOf[ExampleView], "2"))
  scala.io.StdIn.readLine()
  persistentActor ! "increment"
  persistentActor ! "increment"
  scala.io.StdIn.readLine()
  view ! "print"
  view2 ! "print"
  persistentActor ! "print"
  scala.io.StdIn.readLine()

  system.shutdown()
}
