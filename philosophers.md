---
layout: default
title: Reality Show Philosophers
---

Dijkstra created the dining philosopher's problem as an example of
deadlock in concurrent systems ([dining
philosophers](http://en.wikipedia.org/wiki/Dining_philosophers_problem)
on Wikipedia). Each philosopher must pick up two forks to eat his meal,
but there are not enough forks for all of them to eat at once. The
philosophers must have some sort of strategy to ensure that they don't
all pick up one fork and then wait forever for a second.

Many solutions are possible that avoid deadlock, but STM provides a
particularly straightforward one. ScalaSTM's atomic blocks provide a
means for a philosopher to pick up both forks at the same time, a
capability not available to most implementors.

{% highlight scala %}
  class Fork { val inUse = Ref(false) }

  def meal(left: Fork, right: Fork) {
    // thinking
    atomic { implicit txn =>
      if (left.inUse() || right.inUse())
        retry // forks are not both ready, wait
      left.inUse() = true
      right.inUse() = true
    }
    // eating
    atomic { implicit txn =>
      left.inUse() = false
      right.inUse() = false
    }
  }
{% endhighlight %}

This shows that a `Ref[Boolean]` can act like a lock when combined with
`retry`.

Adding a camera {#camera}
---------------

In this era it is more likely that diners would have to fight over forks
on a reality TV show than behind closed doors. Unlike solutions based on
semaphores or agents, the STM solution can easily add the camera's
outside perspective. In a real system the outside view might come from
an administrative console (reading and writing), a checkpointing thread
or a GUI component.

Recording ownership {#ownership}
-------------------

First, we'll change the forks so that they use an `Option` to record
both the existence of an owner and the owner's name. Note that when
using the `Ref` factory method to create a `Ref[Option[String]]` we need
to coerce the type of `None`. If we didn't do this then `owner` would
end up as a `Ref[None]`, which isn't very useful.

We'll also give each philosopher a name and a `Ref[Int]` in which to
record their progress. For convenience, `Ref` and `Ref.View` provide
in-place arithmetic operations such as `+=` for types `A` that have an
associated `Numeric[A]`.

{% highlight scala %}
  class Fork {
    val owner = Ref(None : Option[String])    
  }

  class PhilosopherThread(val name: String, val meals: Int,
          left: Fork, right: Fork) extends Thread {
    val mealsEaten = Ref(0)

    override def run() {
      for (m <- 0 until meals) {
        // thinking
        atomic { implicit txn =>
          if (!(left.owner().isEmpty && right.owner().isEmpty))
            retry
          left.owner() = Some(name)
          right.owner() = Some(name)
        }
        // eating
        atomic { implicit txn =>
          mealsEaten += 1
          left.owner() = None
          right.owner() = None
        }
      }
    }
  }
{% endhighlight %}

Capturing a snapshot {#image}
--------------------

Capturing an image of the state of the system is now as easy as
iterating over the forks and philosophers inside an atomic block. It is
important that transactions don't access a mutable object (or a `var`)
that is declared outside the atomic block. The mutable `StringBuilder`
below is created inside the atomic block, so it is safe.

{% highlight scala %}
  def image(forks: Seq[Fork], philosophers: Seq[Philosopher]) = {
    atomic { implicit txn =>
      val buf = new StringBuilder
      for (i <- 0 until forks.length)
        buf ++= format("fork %d is owned by %s\n", i, forks(i).owner.single())
      for (p <- philosophers)
        buf ++= format("%s is %3.1f%% done\n", p.name,
            p.mealsEaten.single() * 100.0 / p.meals)
      buf.toString
    }
  }
{% endhighlight %}

Full source {#source}
-----------

The full source for this example is available as part of the ScalaSTM
source on github:
[RealityShowPhilosophers.scala](https://github.com/nbronson/scala-stm/blob/master/src/test/scala/scala/concurrent/stm/examples/RealityShowPhilosophers.scala).
It includes a camera thread that prints an image 60 times a second, as
well as a bit of machinery to handle thread shutdown.

Below is an excerpt from running `RealityShowPhilosophers`. Note that
since forks are picked up and put down instantaneously, the camera never
observes a philosopher holding only a single fork.

{% highlight java %}
...
fork 0 is owned by Some(Socrates)
fork 1 is owned by Some(Hippocrates)
fork 2 is owned by Some(Hippocrates)
fork 3 is owned by None
fork 4 is owned by Some(Socrates)
Aristotle is 30.86% done
Hippocrates is 28.58% done
Plato is 22.73% done
Pythagoras is 22.67% done
Socrates is 18.60% done

fork 0 is owned by Some(Aristotle)
fork 1 is owned by Some(Aristotle)
fork 2 is owned by Some(Plato)
fork 3 is owned by Some(Plato)
fork 4 is owned by None
Aristotle is 39.23% done
Hippocrates is 31.92% done
Plato is 28.39% done
Pythagoras is 26.52% done
Socrates is 22.13% done
...
{% endhighlight %}