package icenet

import chisel3._
import chisel3.util._
import chisel3.experimental.{IntParam, ChiselEnum}
import testchipip.{StreamIO, StreamChannel, TLHelper, ClockedIO}
import IceNetConsts._

class UINTEncoder extends Module {
  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new StreamChannel(64)))
    val out = Decoupled(new StreamChannel(64))
  })

  io.in.valid <> io.out.valid
  io.in.ready <> io.out.ready
  io.in.bits.last <> io.out.bits.last
  io.in.bits.keep <> io.out.bits.keep
  // for testing
  //io.in.bits.data <> io.out.bits.data

  // state machine to parse headers
  val sWordOne :: sWordTwo :: sWordThree :: sWordFour :: sWordFive :: sWordSix :: sWordSeven :: sWordEight :: sWaitEnd :: Nil = Enum(9)
  val state = RegInit(sWordOne)

  // for debugging
  val reg_payload = RegInit(0.U(64.W))
  dontTouch(reg_payload)

  val num_groups = 10 // ceil(64/7)
  val groups = Wire(Vec(num_groups, UInt(7.W)))
  val has_bits_set = Wire(Vec(num_groups, Bool()))
  val most_sig_grp = Wire(UInt(8.W))
  val new_groups = Wire(Vec(num_groups, UInt(8.W)))

  switch (state) {
    is (sWordOne) {
      reg_payload := 0.U(64.W)
      transition(sWordTwo)
    }
    is (sWordTwo) {
      transition(sWordThree)
    }
    is (sWordThree) {
      transition(sWordFour)
    }
    is (sWordFour) {
      transition(sWordFive)
    }
    is (sWordFive) {
      transition(sWordSix)
    }
    is (sWordSix) {
      transition(sWordSeven)
    }
    is (sWordSeven) {
      transition(sWordEight)
    }
    is (sWordEight) {
      transition(sWaitEnd)
    }
    is (sWaitEnd) {
      when (io.in.valid && io.in.bits.last) {
        state := sWordOne
        // write payload to reg for testing
        //reg_payload := io.in.bits.data

        // split 64-bit input data into groups of 7-bits
        // NOTE: order is reversed (e.g. groups(9) gets io.in(6,0))
        for (i <- 0 until (num_groups - 1)) {
          groups(num_groups - i - 1) := io.in.bits.data(7*i+6, 7*i)
        }
        groups(0) := Cat(0.U(6.W), io.in.bits.data(63))

        // compute which groups have bits set
        has_bits_set := groups.map( _.orR )

        // Use priority encoder to find the most significant group with a bit set
        // NOTE: the order of groups is reversed from the original input data, so
        // the most significant group is actually the smallest index.
        most_sig_grp := PriorityEncoder(has_bits_set.asUInt)

        // Append one bit to each group, which is 1 for all groups except group X.
        // Also, assign the most significant group to the 0th group. We don't care
        // about any groups b/w 0 and most_sig_grp.        
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
    }
  }

  def transition(next_state: UInt) = {
    when (io.in.valid) {
      when (!io.in.bits.last) {
        state := next_state
      } .otherwise {
        state := sWordOne
      }
    }
  }

  
  // split 64-bit input data into groups of 7-bits
  // NOTE: order is reversed (e.g. groups(9) gets io.in(6,0))
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
    val in = Flipped(Decoupled(new StreamChannel(64)))
    val out = Decoupled(new StreamChannel(64))
  })
  
  io.in.valid <> io.out.valid
  io.in.ready <> io.out.ready
  io.in.bits.last <> io.out.bits.last
  io.in.bits.keep <> io.out.bits.keep
  // for testing
  io.in.bits.data <> io.out.bits.data
 
 /* 
  val num_groups = 10
  val groups = Wire(Vec(num_groups, UInt(7.W)))
  for(i <- 0 until (num_groups - 1)) {
    groups(num_groups - i - 1) := io.in.bits.data(8*i+6, 8*i)
  }
  groups(0) := Cat(0.U(7.W), io.in.bits.data(79))

  io.out.bits.data := groups.asUInt
  */
}
