---
layout: default
title: Map + Set Snapshots
---

A feature of the `TMap` and `TSet` collections provided with ScalaSTM is
fast (*O (1)*) snapshots. We use these snapshots to provide consistent
iteration of `TMap.View` and `TSet.View`, even when the iteration is
performed outside a transaction.

Consistent iteration {#iter}
--------------------

`TMap.View` extends `mutable.MapLike`, so it provides all of the
rich-trait functionality of a `Map`. It is pretty clear that functions
like `get` and `put` should be atomic even when they are called outside
an atomic block, but what about `iterator`? An iterator can be held for
a long time, so what should it produce when the underlying collection
continues to be changed by concurrent threads?

We have chosen to make `TMap.View.iterator` and `TSet.View.iterator`
return an iterator over an atomic snapshot of the collection. The
`iterator` method acts as if it has made a copy of the entire collection
and returned an iterator over the copy. Concurrent updates can proceed
uninhibited.

Much of the rich-trait functionality provided by `Map` and `Set` is
implemented in terms of `iterator`, so the snapshot isolation provided
by these methods avoids surprises. For example, the following code might
print zero or two matches if `m.iterator` wasn't consistent

{% highlight scala %}
val m = TMap("one" -> 1).single

(new Thread { override def run {
  atomic { implicit txn =>
    m -= "one"
    m += ("ONE" -> 1)
  }
} }).start

for ((k, v) <- m; if v == 1) println(k)
{% endhighlight %}

Manual snapshots {#manual}
----------------

You can directly access the snapshot functionality via the functions
`snapshot` and `clone`. `TMap` and `TMap.View` return an `immutable.Map`
from `snapshot`; `TSet` and `TSet.View` return an `immutable.Set` [^1].
The `TMap` or `TSet` returned from `clone` is a fully-functional
transactional (and concurrent) collection.

How does it work? {#how}
-----------------

Underneath, `TMap` and `TSet` use mutable hash tries constructed from
`Ref`-s, with generation numbers that control copy-on-write. The
algorithm is a novel hybrid of Nathan Bronson's SnapTree [^2] and
Transactional Predication [^3], described in Chapter 4 of his thesis
[^4]. As with transactional predication, no atomic block is required for
accessing the collection outside a transaction, which substantially
reduces overheads when no composition of operations is required.

[^1]: The `snapshot` operation on `TMap` and `TSet` is actually provided
    via an implicit conversion, so you can call it even if you don't see
    it in their class definitions.

[^2]: N. G. Bronson, J. Casper, H. Chafi, and K. Olukotun. A Practical
    Concurrent Binary Search Tree. In *PPoPP '10: Proceedings of the
    15th Annual Symposium on Principles and Practice of Parallel
    Programming*, 2010.

[^3]: N. G. Bronson, J. Casper, H. Chafi, and K. Olukotun. Transactional
    Predication: High-Performance Concurrent Sets and Maps for STM. In
    *PODC'10: Proceedings of the 29th Annual ACM Conference on
    Principles of Distributed Computing*, 2010.

[^4]: N. G. Bronson. Composable Operations on High-Performance
    Concurrent Collections. *Ph.D. Dissertation*, Stanford University,
    2011. <http://purl.stanford.edu/gm457gs5369>