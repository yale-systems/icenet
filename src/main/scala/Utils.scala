package icenet

import chisel3._
import chisel3.util._
import chisel3.experimental.IntParam
import IceNetConsts._

class UINTEncodeBlackBox(val w: Int) extends BlackBox(Map("UINT_BITS" -> IntParam(w))) with HasBlackBoxResource
{
  addResource("/vsrc/uint_decode_64.v")

  val io = IO(new Bundle{
    val clk = Input(Clock())
    val aresetn = Input(Bool())
    val s_axis_tvalid = Input(Bool())
    val s_axis_tdata = Input(UInt(w.W))
    val m_axis_tvalid = Output(Bool())
    val m_axis_tdata = Output(UInt(w.W))
  })
}

class UINTDecodeBlackBox(val w: Int) extends BlackBox(Map("UINT_BITS" -> IntParam(w))) with HasBlackBoxResource
{
  addResource("/vsrc/uint_decode_64.v")

  val io = IO(new Bundle{
    val clk = Input(Clock())
    val aresetn = Input(Bool())
    val s_axis_tvalid = Input(Bool())
    val s_axis_tdata = Input(UInt(w.W))
    val m_axis_tvalid = Output(Bool())
    val m_axis_tdata = Output(UInt(w.W))
  })
}
