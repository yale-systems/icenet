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
module uint_encode_64 #(
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
  reg [ENCODED_BITS-1:0] tmp_reg = 64'd0;
  reg [ENCODED_BITS-1:0] out_reg = 64'd0;

  reg [TUSER_BITS-1:0] byte_count = 6'd0;

  localparam IDLE   = 3'd0,
             SPLIT  = 3'd1,
             MSB    = 3'd2,
             SWAP   = 3'd3,
             OUTPUT = 3'd4,
             DELAY  = 3'd5;

  reg [2:0] state = IDLE;

  always@(posedge clk) begin
    if(~aresetn) begin
      m_axis_tvalid <= 0;
      m_axis_tdata  <= 0;
      m_axis_tuser  <= 0;
      state <= IDLE;
      in_reg  <= 64'd0;
      tmp_reg <= 64'd0;
      out_reg <= 64'd0;
      byte_count <= 6'd0;
    end else begin
      case (state)

        IDLE: begin
          if(s_axis_tvalid == 1) begin
            in_reg <= s_axis_tdata;
            state <= SPLIT;
          end else begin
            m_axis_tvalid <= 0;
            m_axis_tdata  <= 0;
            m_axis_tuser  <= 0;
            in_reg  <= 64'd0;
            tmp_reg <= 64'd0;
            out_reg <= 64'd0;
            byte_count <= 6'd0;
          end
        end

        SPLIT: begin
          // split groups of 7 bits from the input
          tmp_reg[6:0]   = in_reg[6:0];
          tmp_reg[14:8]  = in_reg[13:7];
          tmp_reg[22:16] = in_reg[20:14];
          tmp_reg[30:24] = in_reg[27:21];
          tmp_reg[38:32] = in_reg[34:28];
          tmp_reg[46:40] = in_reg[41:35];
          tmp_reg[54:48] = in_reg[48:42];
          tmp_reg[62:56] = in_reg[55:49];
          // tmp_reg[70:64] = in_reg[62:56];
          // tmp_reg[78:72] = in_reg[69:63];
          state <= MSB;
        end

        MSB: begin
          // set MSBs if anything follows
          if(tmp_reg[63:8]  > 0) begin tmp_reg[7]  = 1; end
          if(tmp_reg[63:16] > 0) begin tmp_reg[15] = 1; end
          if(tmp_reg[63:24] > 0) begin tmp_reg[23] = 1; end
          if(tmp_reg[63:32] > 0) begin tmp_reg[31] = 1; end
          if(tmp_reg[63:40] > 0) begin tmp_reg[39] = 1; end
          if(tmp_reg[63:48] > 0) begin tmp_reg[47] = 1; end
          if(tmp_reg[63:56] > 0) begin tmp_reg[55] = 1; end
          // if(tmp_reg[79:64] > 0) begin tmp_reg[63] = 1; end
          // if(tmp_reg[79:72] > 0) begin tmp_reg[71] = 1; end
          state <= SWAP;
        end

        SWAP: begin
          // copy the lowest byte if it's not empty
          if(tmp_reg[7:0] > 0) begin out_reg[63:56] = tmp_reg[7:0]; byte_count = 6'd1; end

          // copy remaining bytes if the previous byte's MSB is set
          // and update encoded bytes count
          // if(tmp_reg[7]  == 1) begin out_reg[71:64] = tmp_reg[15:8];  byte_count = 6'd2; end
          // if(tmp_reg[15] == 1) begin out_reg[63:56] = tmp_reg[23:16]; byte_count = 6'd3; end
          // if(tmp_reg[23] == 1) begin out_reg[55:48] = tmp_reg[31:24]; byte_count = 6'd4; end
          // if(tmp_reg[31] == 1) begin out_reg[47:40] = tmp_reg[39:32]; byte_count = 6'd5; end
          // if(tmp_reg[39] == 1) begin out_reg[39:32] = tmp_reg[47:40]; byte_count = 6'd6; end
          // if(tmp_reg[47] == 1) begin out_reg[31:24] = tmp_reg[55:48]; byte_count = 6'd7; end
          // if(tmp_reg[55] == 1) begin out_reg[23:16] = tmp_reg[63:56]; byte_count = 6'd8; end
          if(tmp_reg[7]  == 1) begin out_reg[55:48] = tmp_reg[15:8];  byte_count = 6'd2; end
          if(tmp_reg[15] == 1) begin out_reg[47:40] = tmp_reg[23:16]; byte_count = 6'd3; end
          if(tmp_reg[23] == 1) begin out_reg[39:32] = tmp_reg[31:24]; byte_count = 6'd4; end
          if(tmp_reg[31] == 1) begin out_reg[31:24] = tmp_reg[39:32]; byte_count = 6'd5; end
          if(tmp_reg[39] == 1) begin out_reg[23:16] = tmp_reg[47:40]; byte_count = 6'd6; end
          if(tmp_reg[47] == 1) begin out_reg[15:8] = tmp_reg[55:48]; byte_count = 6'd7; end
          if(tmp_reg[55] == 1) begin out_reg[7:0] = tmp_reg[63:56]; byte_count = 6'd8; end
          // if(tmp_reg[63] == 1) begin out_reg[15:8]  = tmp_reg[71:64]; byte_count = 6'd9; end
          // if(tmp_reg[71] == 1) begin out_reg[7:0]   = tmp_reg[79:72]; byte_count = 6'd10; end
          state <= OUTPUT;
        end

        OUTPUT: begin
          m_axis_tdata  <= out_reg;
          m_axis_tuser  <= byte_count;
          m_axis_tvalid <= 1;
          state <= DELAY;
        end

        DELAY: begin
          m_axis_tdata  <= out_reg;
          m_axis_tuser  <= byte_count;
          m_axis_tvalid <= 1;
          state <= IDLE;
        end
      endcase
    end
  end

endmodule: uint_encode_64
