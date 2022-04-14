/**************************************************************************************
* Copyright (c) 2020 Institute of Computing Technology, CAS
* Copyright (c) 2020 University of Chinese Academy of Sciences
* 
* NutShell is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2. 
* You may obtain a copy of Mulan PSL v2 at:
*             http://license.coscl.org.cn/MulanPSL2 
* 
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND, EITHER 
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT, MERCHANTABILITY OR 
* FIT FOR A PARTICULAR PURPOSE.  
*
* See the Mulan PSL v2 for more details.  
***************************************************************************************/

package top

import nutcore._
import system.NutShell
import device.{AXI4VGA}
import sim.SimTop
import bus.axi4._

import chisel3._
import chisel3.stage._

class Top extends Module {
  val io = IO(new Bundle{})
  val nutshell = Module(new NutShell()(NutCoreConfig()))
  val vga = Module(new AXI4VGA)

  nutshell.io := DontCare
  vga.io := DontCare
  dontTouch(nutshell.io)
  dontTouch(vga.io)
}
class FireSim_A extends Module{
  val io = IO(new Bundle{})
  val nutcore = Module(new NutCore_A()(NutCoreConfig()))
  nutcore.io := DontCare
  dontTouch(nutcore.io)
}

class FireSim_B extends Module{
  val io = IO(new Bundle{})
  val nutshell = Module(new NutShell()(NutCoreConfig()))
  nutshell.io := DontCare
  val converter = Module(new AXI4WRAPConverter)
  nutshell.io.mem <> converter.io.fromCore
  converter.io.toMem := DontCare
  dontTouch(nutshell.io)
  dontTouch(converter.io)
}

class FireSim extends Module {
  val io = IO(new Bundle{})
  val nutshell = Module(new NutShell()(NutCoreConfig()))
  val nutcore = Module(new NutCore_A()(NutCoreConfig()))

  nutshell.io := DontCare
  nutcore.io := DontCare
  nutshell.io.icache <> nutcore.io.icache
  nutshell.io.dcache <> nutcore.io.dcache
  //val xbarA = Module(new SimpleBusCrossbarNto1(2))
  //xbar.io.in(0) <> nutcore.io.icache
  //xbar.io.in(1) <> nutcore.io.dcache
  //val xbarB = Module(new SimpleBusCrossbar1toN())
  nutcore.io.dempty := nutshell.io.dempty
  nutcore.io.iempty := nutshell.io.iempty
  nutshell.io.flush := nutcore.io.flush
  dontTouch(nutshell.io)
  val converter = Module(new AXI4WRAPConverter)
  nutshell.io.mem <> converter.io.fromCore
  converter.io.toMem := DontCare
  dontTouch(converter.io)
}

object TopMain extends App {
  def parseArgs(info: String, args: Array[String]): String = {
    var target = ""
    for (arg <- args) { if (arg.startsWith(info + "=") == true) { target = arg } }
    require(target != "")
    target.substring(info.length()+1)
  }
  val board = parseArgs("BOARD", args)
  val core = parseArgs("CORE", args)
  
  val s = (board match {
    case "sim"    => Nil
    case "pynq"   => PynqSettings()
    case "axu3cg" => Axu3cgSettings()
    case "PXIe"   => PXIeSettings()
    case "FireSim" => FireSimSettings()
    case "FireSim_A" => FireSimSettings()
    case "FireSim_B" => FireSimSettings()
  } ) ++ ( core match {
    case "inorder"  => InOrderSettings()
    case "ooo"  => OOOSettings()
    case "embedded"=> EmbededSettings()
  } )
  s.foreach{Settings.settings += _} // add and overwrite DefaultSettings
  println("====== Settings = (" + board + ", " +  core + ") ======")
  Settings.settings.toList.sortBy(_._1)(Ordering.String).foreach {
    case (f, v: Long) =>
      println(f + " = 0x" + v.toHexString)
    case (f, v) =>
      println(f + " = " + v)
  }
  if (board == "sim") {
    (new ChiselStage).execute(args, Seq(
      ChiselGeneratorAnnotation(() => new SimTop))
    )
  } else if (board == "FireSim"){
    (new ChiselStage).execute(args, Seq(
      ChiselGeneratorAnnotation(() => new FireSim))
    )
  } else if (board == "FireSim_A"){
    (new ChiselStage).execute(args, Seq(
      ChiselGeneratorAnnotation(() => new FireSim_A))
    )
  } else if (board == "FireSim_B"){
    (new ChiselStage).execute(args, Seq(
      ChiselGeneratorAnnotation(() => new FireSim_B))
    )
  } else {
    (new ChiselStage).execute(args, Seq(
      ChiselGeneratorAnnotation(() => new Top))
    )
  }
}
