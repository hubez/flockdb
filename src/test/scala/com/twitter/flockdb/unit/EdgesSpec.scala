package com.twitter.flockdb.unit

import scala.collection.mutable
import com.twitter.gizzard.Future
import com.twitter.gizzard.jobs.SchedulableWithTasks
import com.twitter.gizzard.scheduler.{JobScheduler, PrioritizingJobScheduler}
import com.twitter.gizzard.nameserver.NameServer
import com.twitter.gizzard.shards.ShardInfo
import com.twitter.gizzard.thrift.conversions.Sequences._
import com.twitter.xrayspecs.Time
import com.twitter.xrayspecs.TimeConversions._
import org.specs.Specification
import org.specs.mock.{ClassMocker, JMocker}
import jobs.multi.{RemoveAll, Archive, Unarchive}
import jobs.single.{Add, Remove}
import conversions.Edge._
import shards.Shard
import thrift.{FlockException, Page, Results}
import State._


object EdgesSpec extends Specification with JMocker with ClassMocker {
  "Edges" should {
    val FOLLOWS = 1

    val bob = 1L
    val mary = 2L

    val nameServer = mock[NameServer[Shard]]
    val forwardingManager = mock[ForwardingManager]
    val shard = mock[Shard]
    val scheduler = mock[PrioritizingJobScheduler]
    val future = mock[Future]
    val copyFactory = mock[gizzard.jobs.CopyFactory[Shard]]
    val edges = new Edges(nameServer, forwardingManager, copyFactory, scheduler, future)

    "counts_of_destinations_for" in {
      val list = new mutable.ArrayBuffer[Long]
      list += bob
      list += mary

      expect {
        one(forwardingManager).find(bob, FOLLOWS, Direction.Forward) willReturn shard
        one(forwardingManager).find(mary, FOLLOWS, Direction.Forward) willReturn shard
        one(shard).counts(list, mutable.Map.empty[Long, Int])
      }
      edges.counts_of_destinations_for(list.pack, FOLLOWS).toList mustEqual List(0, 0).pack.toList
    }

    "counts_of_sources_for" in {
      val list = new mutable.ArrayBuffer[Long]
      list += bob
      list += mary

      expect {
        one(forwardingManager).find(bob, FOLLOWS, Direction.Backward) willReturn shard
        one(forwardingManager).find(mary, FOLLOWS, Direction.Backward) willReturn shard
        one(shard).counts(list, mutable.Map.empty[Long, Int])
      }
      edges.counts_of_sources_for(list.pack, FOLLOWS).toList mustEqual List(0, 0).pack.toList
    }

    "add" in {
      Time.freeze()
      val job = Add(bob, FOLLOWS, mary, Time.now.inMillis, Time.now)
      expect {
        one(scheduler).apply(Priority.High.id, new SchedulableWithTasks(List(job)))
      }
      edges.execute(Select(bob, FOLLOWS, mary).add.toThrift)
    }

    "add_at" in {
      val job = Add(bob, FOLLOWS, mary, Time.now.inMillis, Time.now)
      expect {
        one(scheduler).apply(Priority.High.id, new SchedulableWithTasks(List(job)))
      }
      edges.execute(Select(bob, FOLLOWS, mary).addAt(Time.now).toThrift)
    }

    "remove" in {
      Time.freeze()
      val job = Remove(bob, FOLLOWS, mary, Time.now.inMillis, Time.now)
      expect {
        one(scheduler).apply(Priority.High.id, new SchedulableWithTasks(List(job)))
      }
      edges.execute(Select(bob, FOLLOWS, mary).remove.toThrift)
    }

    "remove_at" in {
      val job = Remove(bob, FOLLOWS, mary, Time.now.inMillis, Time.now)
      expect {
        one(scheduler).apply(Priority.High.id, new SchedulableWithTasks(List(job)))
      }
      edges.execute(Select(bob, FOLLOWS, mary).removeAt(Time.now).toThrift)
    }

    "contains" in {
      expect {
        one(forwardingManager).find(bob, FOLLOWS, Direction.Forward) willReturn shard
        one(shard).get(bob, mary) willReturn Some(new Edge(bob, mary, 0, Time.now, 1, State.Normal))
      }
      edges.contains(bob, FOLLOWS, mary) must beTrue
    }
  }
}
