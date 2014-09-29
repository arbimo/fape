package planstack.constraints.stnu

import org.scalatest.FunSuite

import planstack.constraints.stn.Predef._

class IDCSuite extends FunSuite {

  for(idc <- getAllISTNU[String]) {

    test("Pseudo controllable: "+idc.getClass.getName) {
      val A = idc.addVar()
      val B = idc.addVar()
      val C = idc.addVar()
      val D = idc.addVar()

      idc.enforceBefore(A, B)
      assert(idc.consistent)
      idc.enforceBefore(A, C)
      assert(idc.consistent)
      idc.enforceBefore(A, D)
      assert(idc.consistent)

      idc.addContingent(B, C, 10, 15)
      assert(idc.consistent)

      idc.enforceBefore(C, D)
      assert(idc.consistent)
      idc.enforceInterval(A, D, 16, 16)

      assert(idc.consistent)
    }
  }

  for(idc <- getAllISTNU[String]) {

    test("Not pseudo controllable: "+idc.getClass.getName) {
      val A = idc.addVar()
      val B = idc.addVar()
      val C = idc.addVar()
      val D = idc.addVar()

      idc.enforceBefore(A, B)
      idc.enforceBefore(A, C)
      idc.enforceBefore(A, D)

      idc.addContingent(B, C, 10, 15)

      idc.enforceBefore(C, D)

      assert(idc.consistent)
      idc.enforceInterval(A, D, 12, 12)

      assert(!idc.consistent)
    }
  }
}
