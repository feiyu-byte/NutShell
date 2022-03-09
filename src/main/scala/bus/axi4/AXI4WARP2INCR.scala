package bus.axi4

import chisel3._
import chisel3.util._



class AXI4WARPConverter (val dataBits: Int = AXI4Parameters.dataBits, val idBits: Int = AXI4Parameters.idBits) extends Module {
	
	val io = IO(new Bundle{
		val fromCore = Flipped(new AXI4(dataBits,idBits))
		val toMem = new AXI4(dataBits,idBits)
	})

	val s_Idle :: s_ReadReq :: s_ReadMemResp :: s_ReadCoreResp:: s_WriteReq :: s_WriteCoreResp :: s_WriteMemResp :: s_WriteBResp :: Nil = Enum(8)

	val r_state = RegInit(s_Idle)
	val w_state = RegInit(s_Idle)
	when(io.toMem.ar.fire()){r_state := s_ReadMemResp}
	when(io.toMem.r.fire() && io.toMem.r.bits.last){r_state := s_ReadCoreResp}
        when(io.fromCore.r.fire() && io.fromCore.r.bits.last){r_state := s_Idle}
	when(io.fromCore.aw.fire()){w_state := s_WriteCoreResp}
	when(io.fromCore.w.fire() && io.fromCore.w.bits.last){w_state := s_WriteMemResp}
	when(io.toMem.w.fire() && io.toMem.w.bits.last){w_state := s_WriteBResp}
        when(io.fromCore.b.fire()){w_state := s_Idle}

	val writeNotWRAP = RegEnable(io.fromCore.aw.bits.burst =/= AXI4Parameters.BURST_WRAP, io.fromCore.aw.valid)
	val readNotWRAP = RegEnable(io.toMem.ar.bits.burst =/= AXI4Parameters.BURST_WRAP , io.fromCore.ar.valid)

	when(io.fromCore.b.fire()){writeNotWRAP := false.B}
	when(io.toMem.r.bits.last && io.toMem.r.fire()){readNotWRAP := false.B}

	val awPaddingWidth = if(dataBits == 64) 6 else 5//log2Ceil((io.fromCore.aw.bits.len + 1.U)*(1.U << io.fromCore.aw.bits.size)) //log2((7+1)*(1<<3))=6
	val arPaddingWidth = if(dataBits == 64) 6 else 5//log2Ceil((io.fromCore.ar.bits.len + 1.U)*(1.U << io.fromCore.ar.bits.size))

	val awPrimitiveAddr = RegEnable(io.fromCore.aw.bits.addr, io.fromCore.aw.fire())
	val arPrimitiveAddr = RegEnable(io.fromCore.ar.bits.addr, io.fromCore.ar.fire())

	val wdataBuffer = Reg(Vec(8,UInt(dataBits.W)))
	val wstrbBuffer = Reg(Vec(8,UInt((dataBits/8).W)))
	val rdataBuffer = Reg(Vec(8,UInt(dataBits.W)))

	val (readBeatsCnt,rcond) = Counter(io.toMem.r.fire()||io.fromCore.r.fire(),7)
	val (writeBeatsCnt,wcond) = Counter(io.toMem.w.fire()||io.fromCore.w.fire(),7)

	when(io.fromCore.aw.fire()){
		writeBeatsCnt := io.fromCore.aw.bits.addr(awPaddingWidth-1,3)
	}
	when(io.fromCore.w.fire() && io.fromCore.w.bits.last){writeBeatsCnt := 0.U}

	when(io.toMem.ar.fire()){readBeatsCnt := 0.U}
	when(io.toMem.r.fire() && io.toMem.r.bits.last){
		readBeatsCnt := arPrimitiveAddr(arPaddingWidth-1,3)
	}

	when(io.toMem.r.fire()){rdataBuffer(readBeatsCnt) := io.toMem.r.bits.data}
	when(io.fromCore.w.fire()){
		wdataBuffer(writeBeatsCnt) := io.fromCore.w.bits.data
		wstrbBuffer(writeBeatsCnt) := io.fromCore.w.bits.strb
	}
	
	val bid = RegEnable(io.fromCore.aw.bits.id,io.fromCore.aw.fire())
	when(writeNotWRAP){
		io.toMem.aw <> io.fromCore.aw
		io.toMem.w <> io.fromCore.w
		io.toMem.b <> io.fromCore.b
	}.otherwise{
		io.toMem.aw <> io.fromCore.aw
		io.toMem.aw.bits.burst := AXI4Parameters.BURST_INCR
		io.toMem.aw.bits.addr := Cat(io.fromCore.aw.bits.addr(AXI4Parameters.addrBits-1,awPaddingWidth),0.U(awPaddingWidth.W))
		io.toMem.w.valid := w_state === s_WriteMemResp
		io.toMem.w.bits.data := wdataBuffer(writeBeatsCnt)
		io.toMem.w.bits.last := writeBeatsCnt === 7.U && io.toMem.w.fire()
		io.toMem.w.bits.strb := wstrbBuffer(writeBeatsCnt)
		io.toMem.b.ready := true.B

		io.fromCore.w.ready := w_state === s_WriteCoreResp
		io.fromCore.b.valid := w_state === s_WriteBResp
		io.fromCore.b.bits.resp := AXI4Parameters.RESP_OKAY
		io.fromCore.b.bits.user := io.toMem.b.bits.user
		io.fromCore.b.bits.id := bid
	}

	val rid = RegEnable(io.fromCore.ar.bits.id,io.fromCore.ar.fire())
	when(readNotWRAP){
		io.toMem.ar <> io.fromCore.ar
		io.toMem.r <> io.fromCore.r
	}.otherwise{
		// cache Mem rdata as INCR
		io.toMem.ar <> io.fromCore.ar
		io.toMem.ar.bits.burst := AXI4Parameters.BURST_INCR
		io.toMem.ar.bits.addr := Cat(io.fromCore.ar.bits.addr(AXI4Parameters.addrBits-1,arPaddingWidth),0.U(arPaddingWidth.W))
		io.toMem.r.ready := true.B
		// send to Core as WRAP
		io.fromCore.r.valid := r_state === s_ReadCoreResp
		io.fromCore.r.bits.data := rdataBuffer(readBeatsCnt)
		io.fromCore.r.bits.last := readBeatsCnt === (arPrimitiveAddr(arPaddingWidth-1,3) - 1.U) && io.fromCore.r.fire()
		io.fromCore.r.bits.user := io.toMem.r.bits.user
		io.fromCore.r.bits.resp := AXI4Parameters.RESP_OKAY
		io.fromCore.r.bits.id := rid
	}

}
