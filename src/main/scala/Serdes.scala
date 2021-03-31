package icenet

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, ChiselEnum}
import testchipip.{StreamIO, StreamChannel, TLHelper, ClockedIO}
import IceNetConsts._

class UINTEncoder extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new StreamChannel(64)))
    val out = Decoupled(new StreamChannel(80))
  })

  io.in.valid <> io.out.valid
  io.in.ready <> io.out.ready
  io.in.bits.last <> io.out.bits.last
  io.in.bits.keep <> io.out.bits.keep

  // split 64-bit input data into groups of 7-bits
  // NOTE: order is reversed (e.g. groups(9) gets io.in(6,0))
  val num_groups = 10 // ceil(64/7)
  val groups = Wire(Vec(num_groups, UInt(7.W)))
  for (i <- 0 until (num_groups - 1)) {
    groups(num_groups - i - 1) := io.in.bits.data(7*i+6, 7*i)
  }
  groups(0) := Cat(0.U(6.W), io.in.bits.data(63))

  // compute which groups have bits set
  val has_bits_set = Wire(Vec(num_groups, Bool()))
  has_bits_set := groups.map( _.orR )

  // Use priority encoder to find the most significant group with a bit set
  // NOTE: the order of groups is reversed from the original input data, so
  // the most significant group is actually the smallest index.
  val most_sig_grp = Wire(UInt(8.W))
  most_sig_grp := PriorityEncoder(has_bits_set.asUInt)

  // Append one bit to each group, which is 1 for all groups except group X.
  // Also, assign the most significant group to the 0th group. We don't care
  // about any groups b/w 0 and most_sig_grp.
  val new_groups = Wire(Vec(num_groups, UInt(8.W)))
  new_groups(0) := Cat(0.U(1.W), groups(most_sig_grp))
  for (i <- 1 until num_groups) {
    when (i.U + most_sig_grp < num_groups.U) {
      new_groups(i) := Cat(1.U(1.W), groups(i.U + most_sig_grp))
    } .otherwise {
      new_groups(i) := 0.U
    }
  }

  io.out.bits.data := new_groups.asUInt
}


class UINTDecoder extends Module
{
  val io = IO(new Bundle{
    val in = Flipped(Decoupled(new StreamChannel(80)))
    val out = Decoupled(new StreamChannel(64))
  })

  io.in.valid <> io.out.valid
  io.in.ready <> io.out.ready
  io.in.bits.last <> io.out.bits.last
  io.in.bits.keep <> io.out.bits.keep
  
  val num_groups = 10
  val groups = Wire(Vec(num_groups, UInt(7.W)))
  for(i <- 0 until (num_groups - 1)) {
    groups(num_groups - i - 1) := io.in.bits.data(8*i+6, 8*i)
  }
  groups(0) := Cat(0.U(7.W), io.in.bits.data(79))

  io.out.bits.data := groups.asUInt
}
