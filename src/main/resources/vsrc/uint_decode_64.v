// *************************************************************************
//
// Copyright (c) 2020 Stanford University All rights reserved.
//
// This software was developed by
// Stanford University and the University of Cambridge Computer Laboratory
// under National Science Foundation under Grant No. CNS-0855268,
// the University of Cambridge Computer Laboratory under EPSRC INTERNET Project EP/H040536/1 and
// by the University of Cambridge Computer Laboratory under DARPA/AFRL contract FA8750-11-C-0249 ("MRC2"),
// as part of the DARPA MRC research programme.
//
// @NETFPGA_LICENSE_HEADER_START@
//
// Licensed to NetFPGA C.I.C. (NetFPGA) under one or more contributor
// license agreements.  See the NOTICE file distributed with this work for
// additional information regarding copyright ownership.  NetFPGA licenses this
// file to you under the NetFPGA Hardware-Software License, Version 1.0 (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at:
//
//   http://www.netfpga-cic.org
//
// Unless required by applicable law or agreed to in writing, Work distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations under the License.
//
// @NETFPGA_LICENSE_HEADER_END@
// *************************************************************************
module uint_decode_64 #(
  parameter UINT_BITS = 64,
  parameter ENCODED_BITS = 64,
  parameter TUSER_BITS = $clog2(ENCODED_BITS/2)
) (
  input                         s_axis_tvalid,
  input         [UINT_BITS-1:0] s_axis_tdata,

  output reg                    m_axis_tvalid,
  output reg [ENCODED_BITS-1:0] m_axis_tdata,
  output reg   [TUSER_BITS-1:0] m_axis_tuser, // # of encoded bytes

  input                         clk,
  input                         aresetn // active low
);

  /* Implement functionality that converts uint into encoded format
   * Possible approach:
   *   - Split input uint bus into groups of 7 bits
   *   - Bitwise OR each group together (determine if any bits are set in the
   *     group)
   *   - Use priority encoder to find the most significant group with a bit
   *     set (group X)
   *   - Append one bit to each group, which is 0 for all groups except group X
   *   - Swap the order of all groups
   *   - # of encoded bytes is the index of group X (output of priority
   *   encoder)
   */

  reg [ENCODED_BITS-1:0] in_reg  = 64'd0;
  // reg [ENCODED_BITS-1:0] tmp_reg = 64'd0;
  reg [ENCODED_BITS-1:0] out_reg = 64'd0;

  // reg [TUSER_BITS-1:0] byte_count = 6'd0;

  localparam IDLE   = 3'd0,
            //  SPLIT  = 3'd1,
            //  MSB    = 3'd2,
             SWAP   = 3'd1,
             OUTPUT = 3'd2,
             DELAY  = 3'd3;

  reg [2:0] state = IDLE;

  always@(posedge clk) begin
    if(~aresetn) begin
      m_axis_tvalid <= 0;
      m_axis_tdata  <= 0;
      m_axis_tuser  <= 0;
      state <= IDLE;
      in_reg  <= 64'd0;
      // tmp_reg <= 64'd0;
      out_reg <= 64'd0;
      // byte_count <= 6'd0;
    end else begin
      case (state)

        IDLE: begin
          if(s_axis_tvalid == 1) begin
            in_reg <= s_axis_tdata;
            state <= SWAP;
          end else begin
            m_axis_tvalid <= 0;
            m_axis_tdata  <= 0;
            m_axis_tuser  <= 0;
            in_reg  <= 64'd0;
            // tmp_reg <= 64'd0;
            out_reg <= 64'd0;
            // byte_count <= 6'd0;
          end
        end

        SWAP: begin
          // copy the first byte if it's not empty
          if(in_reg[63:56] > 0) begin out_reg[6:0] = in_reg[62:56]; end

          // copy remaining bytes if the previous byte's MSB is set
          if(in_reg[63] == 1) begin out_reg[13:7]  = in_reg[54:48]; end
          if(in_reg[55] == 1) begin out_reg[20:14] = in_reg[46:40]; end
          if(in_reg[47] == 1) begin out_reg[27:21] = in_reg[38:32]; end
          if(in_reg[39] == 1) begin out_reg[34:28] = in_reg[30:24]; end
          if(in_reg[31] == 1) begin out_reg[41:35] = in_reg[22:16]; end
          if(in_reg[23] == 1) begin out_reg[48:42] = in_reg[14:8];  end
          if(in_reg[15] == 1) begin out_reg[55:49] = in_reg[6:0];   end

          state <= OUTPUT;
        end

        OUTPUT: begin
          m_axis_tdata  <= out_reg;
          m_axis_tuser  <= 0;
          m_axis_tvalid <= 1;
          state <= DELAY;
        end

        DELAY: begin
          m_axis_tdata  <= out_reg;
          m_axis_tuser  <= 0;
          m_axis_tvalid <= 1;
          state <= IDLE;
        end
      endcase
    end
  end

endmodule: uint_decode_64
