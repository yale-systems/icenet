package icenet

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, ChiselEnum}
import testchipip.{StreamIO, StreamChannel, TLHelper, ClockedIO}
import IceNetConsts._

class UINTEncoder(val w: Int) extends Module
{
  val io = IO(new Bundle{
    val in = Flipped(Decoupled(new StreamChannel(w)))
    val out = Decoupled(new StreamChannel(w))
  })

  // TODO: make this more general, currently still assumes 64-bit width

  object State extends ChiselEnum {
    val sIdle, sSplitMSB, sSwap, sOutput = Value
  }

  val state = RegInit(State.sIdle)
  val tmp = VecInit((0.U(w.W)).asBools)
  val tmpUInt = RegInit(0.U(w.W))
  val out = VecInit((0.U(w.W)).asBools)

  switch(state) {
    is (State.sIdle) {
      when(io.in.valid === 1.U) {
        state := State.sSplitMSB
      } .otherwise {
        out := (0.U(w.W)).asBools
        tmp := (0.U(w.W)).asBools
        tmpUInt := 0.U(w.W)
        io.out.valid := false.B
      }
    }
    is (State.sSplitMSB) {
      io.in.ready := false.B

      // split groupps of 7
      tmp(0) := io.in.bits.data(6,0)
      tmp(8) := io.in.bits.data(13,7)
      tmp(16) := io.in.bits.data(20,14)
      tmp(24) := io.in.bits.data(27,21)
      tmp(32) := io.in.bits.data(34,28)
      tmp(40) := io.in.bits.data(41,35)
      tmp(48) := io.in.bits.data(48,42)
      tmp(56) := io.in.bits.data(55,49)

      // set MSBs for high bytes
      when(io.in.bits.data(63,7) > 0.U) {tmp(7) := true.B}
      when(io.in.bits.data(63,14) > 0.U) {tmp(15) := true.B}
      when(io.in.bits.data(63,21) > 0.U) {tmp(23) := true.B}
      when(io.in.bits.data(63,28) > 0.U) {tmp(31) := true.B}
      when(io.in.bits.data(63,35) > 0.U) {tmp(39) := true.B}
      when(io.in.bits.data(63,42) > 0.U) {tmp(47) := true.B}
      when(io.in.bits.data(63,49) > 0.U) {tmp(55) := true.B}

      state := State.sSwap
    }
    is (State.sSwap) {
      tmpUInt := tmp.asUInt

      // copy the lowest byte if it's not empty
      when(tmpUInt(0,7) > 0.U) {out(56) := tmpUInt(0,7)}

      // copy remaining bytes if the previous byte's MSB is set
      when(tmp(7) > 0.U) {out(48) := tmpUInt(8,15)}
      when(tmp(15) > 0.U) {out(40) := tmpUInt(16,23)}
      when(tmp(23) > 0.U) {out(32) := tmpUInt(25,31)}
      when(tmp(31) > 0.U) {out(24) := tmpUInt(32,39)}
      when(tmp(39) > 0.U) {out(16) := tmpUInt(40,47)}
      when(tmp(47) > 0.U) {out(8) := tmpUInt(48,55)}
      when(tmp(55) > 0.U) {out(0) := tmpUInt(56,63)}

      state := State.sOutput
    }
    is (State.sOutput) {
      io.out.bits.data := out.asUInt
      io.out.valid := true.B
      io.in.ready := true.B
      state := State.sIdle
    }
  }
}

class UINTDecoder(val w: Int) extends Module
{
  val io = IO(new Bundle{
    val in = Flipped(Decoupled(new StreamChannel(w)))
    val out = Decoupled(new StreamChannel(w))
  })

  // Control signals
  io.in.valid <> io.out.valid
  io.in.ready <> io.out.ready
  
  // TODO: make this more general, currently still assumes 64-bit width

  val out = Vec(w, false.B)

  // first byte based on contents
  when(io.in.bits.data(63,56) > 0.U) {out(0) := io.in.bits.data(62,56)}

  // remaining bytes based on MSBs
  when(io.in.bits.data(63) > 0.U) {out(7) := io.in.bits.data(54,58)}
  when(io.in.bits.data(55) > 0.U) {out(14) := io.in.bits.data(46,40)}
  when(io.in.bits.data(47) > 0.U) {out(21) := io.in.bits.data(38,32)}
  when(io.in.bits.data(39) > 0.U) {out(28) := io.in.bits.data(30,24)}
  when(io.in.bits.data(31) > 0.U) {out(35) := io.in.bits.data(22,16)}
  when(io.in.bits.data(23) > 0.U) {out(42) := io.in.bits.data(14,8)}
  when(io.in.bits.data(15) > 0.U) {out(49) := io.in.bits.data(6,0)}

  io.out.bits.data := out.asUInt
}
