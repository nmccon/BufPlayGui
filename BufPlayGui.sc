/*
TODO
reinstantiate group after pressing CMD period
update duration box to reflect changes in playback rate
possibly create a player i.e. several GUI instances in one window with output control
many more
*/
BufPlayGui {
	classvar win, sfv, open, boxes, controlView, controlBtn;
	classvar volKnob, volNum, rateKnob, rateNum;
	classvar buffer, player, play, responder;
	classvar sf;
	classvar dbSpec, rateSpec;
	classvar stringCol, backCol;
	classvar recBuffer;


	var server, <>group, <>outbus, <>syncbus;
	//var recBuffer;

	*new { |server, group, outbus, syncbus|
		var serverWarning = false;
		if( Server.default.serverRunning.not {
			"Server not running".warn;
			serverWarning = true
		});

		^super.newCopyArgs(server, group, outbus, syncbus).init;
	}

	init {
		sf = SoundFile();
		dbSpec = ControlSpec(-inf, 6, 'db', 0.0, 0, " dB");
		rateSpec = ControlSpec(0.125, 8.0, \exp, 0, 1);
		stringCol = Color(0.811764705882353, 0.913725490196078, 0.925490196078431);
		backCol = Color(0.2, 0.2, 0.2);

		//sfv
		sfv = SoundFileView()
		.peakColor_(stringCol)
		.background_(backCol)
		.timeCursorOn_(true)
		.timeCursorColor_(Color.blue)
		.timeCursorPosition_(0)
		.drawsWaveForm_(true)
		.gridOn_(false)
		.elasticMode_(true)
		.drawsRMS_(false)
		.drawsCenterLine_(true)
		.drawsBoundingLines_(true);

		sfv.keyDownAction_{|view, char, modifiers, unicode, keycode, key|
			var posData = [view.selections[0][0], (view.selections[0][0] + view.selections[0][1])] / view.numFrames;
			unicode.switch(
				32, {
					if(play.isPlaying, {this.prStop}, {this.prPlay(posData[0], posData[1], dbSpec.map(volKnob.value), rateSpec.map(rateKnob.value))})
				},
				122, {
					view.zoomToFrac(posData[1] - posData[0]);
					view.scrollTo (posData[0]);
					view.scroll (posData[0]);
				},
				90, {
					view.zoom(10);
				},
				104, {
					if(controlBtn.value==0, {controlBtn.valueAction_(1) }, if(controlBtn.value==1, {controlBtn.valueAction_(0)}))
				}
			);
		};

		sfv.mouseUpAction_{|view, char, modifiers, unicode, keycode, key|
			var posData = [view.selections[0][0], (view.selections[0][0] + view.selections[0][1])];
			this.prRanges(posData[0], posData[1], (posData[1] - posData[0])/buffer.sampleRate);
		};

		//open button
		open = Button()
		.states_([["Load", stringCol, Color.clear]])
		.canFocus_(false)
		.action_({|btn|
			if(buffer.isNil.not, {buffer.free});
			Dialog.openPanel({|path|
				sf.openRead(path);
				this.prLoadBuf(path);
				this.prWinTitle(path.basename);
			});
		});

		//number boxes
		boxes = 3.collect{NumberBox()
			.background_(Color.clear)
			.enabled_(false)
			.align_(\center)
			.normalColor_(stringCol)};

		// volume and playback rate views
		volKnob = Knob()
		.value_(dbSpec.unmap(0.707))
		.action_({|kn|
			if(play.isPlaying, {play.set(\amp, dbSpec.map(kn.value))});
			volNum.value_(dbSpec.map(kn.value));
		});

		volNum = NumberBox()
		.value_(dbSpec.map(0.7074715313298).round)
		.minDecimals_(4)
		.background_(Color.clear).enabled_(true).align_(\center).normalColor_(stringCol)
		.action_({|nb|
			volKnob.valueAction_(dbSpec.unmap(nb.value))
		});

		rateKnob = Knob()
		.value_(rateSpec.unmap(1))
		.action_({|kn|
			if(play.isPlaying, {play.set(\rate, rateSpec.map(kn.value))});
			rateNum.value_(rateSpec.map(kn.value)); //needs to display updated value scaled by playback rate
		});

		rateNum = NumberBox()
		.value_(rateSpec.map(0.5))
		.minDecimals_(4)
		.background_(Color.clear).enabled_(true).align_(\center).normalColor_(stringCol)
		.action_({|nb|
			rateKnob.valueAction_(rateSpec.unmap(nb.value))
		});

		//view for volume and playback rate
		controlView = View()
		.visible_(false)
		.background_(Color.clear)
		.layout_(
			GridLayout.columns(
				[ [volKnob], ],
				[ [volNum],],
				[ [TextField().string_("dB").background_(Color.clear).stringColor_(stringCol).enabled_(false).align_(\center)]],
				[ [rateKnob] ],
				[ [rateNum ]],
				[ [TextField().string_("Rate").background_(Color.clear).stringColor_(stringCol).enabled_(false).align_(\center)]],
			)
		)
		.keyDownAction_{|view, char, modifiers, unicode, keycode, key|
			unicode.switch(
				104, {
					if(controlBtn.value==0, {controlBtn.valueAction_(1)}, if(controlBtn.value==1, {controlBtn.valueAction_(0)}))
				}
			)
		};

		//make volume and playback rate visible
		controlBtn = Button()
		.canFocus_(false)
		.states_([["Controls", stringCol, Color.clear], ["Hide", stringCol, Color.clear]])
		.action_({|btn|
			controlView.visible = btn.value.asBoolean;
		});

		this.prInitGUI(open, sfv, boxes[0], boxes[1], boxes[2], controlView)
	}

	prInitGUI {|...views|
		//GUI window
		win = Window("", Rect(906.0, 686.0, 611.0, 263.0)).front.background_(backCol)
		.layout_(GridLayout.rows(
			[ [views[0], columns: 1], nil, nil, nil, [controlBtn, columns: 1]],
			[ [views[1], columns: 6] ],
			[ [views[2], columns: 2, ],[views[3], columns: 2, ], [views[4], columns: 2, ]],
			[ [views[5], columns: 4]]

		))
		.onClose_({buffer.free; play = nil});
	}

	prLoadBuf { |buf|
		fork {
			buffer = Buffer.read(server, buf);
			server.sync;
			sf.close;
		};
		this.prSetSfv
	}

	prSetSfv  {
		sfv.soundfile = sf;
		sfv.read(0, sf.numFrames).refresh;
		sfv.selectNone(0);
		sfv.timeCursorPosition_(0);
	}

	prWinTitle {|title|
		win.name_(title)
	}

	prRanges {|start, end, dur|
		boxes[0].value_(start);
		boxes[1].value_(end);
		boxes[2].value_(dur); //needs to update to reflect change in rate!
	}

	bufFromSelections {|tempBuf, numChans = -1| //requires flucoma library
		FluidBufCompose.processBlocking(
			server: server,
			source: this.getBufnum,
			startFrame: this.getStartFrame,
			numFrames: this.getDurFrames,
			startChan: 0,
			numChans: numChans,
			gain: 1,
			destination: tempBuf,
			destStartFrame: tempBuf.numFrames,
			action: {"done".postln}
		)
	}

	getStartFrame {
		^ boxes[0].value
	}

	getEndFrame {
		^ boxes[1].value
	}

	getDur {
		^ boxes[2].value * rateNum.value.reciprocal
	}

	getDurFrames {
		^ boxes[1].value - boxes[0].value
	}

	getBufnum {
		^ buffer.bufnum
	}

	getSynthName {
		^ play.defName
	}

	getNodeID {
		^ play.nodeID
	}

	getBufAccess {
		^Buffer.cachedBufferAt(server, buffer.bufnum)
	}

	player {
		^ {	|lo = 0, hi = 1, amp = 1, rate = 1|
			var phasor, derivative;
			phasor = Phasor.ar(0, rate * BufRateScale.kr(buffer), lo * BufFrames.kr(buffer), hi * BufFrames.ir(buffer));
			derivative = HPZ1.ar(phasor);
			SendReply.kr(Impulse.kr(50), '/pointer', [A2K.kr(phasor)]);
			Out.ar(syncbus, derivative);
			Out.ar(outbus, BufRd.ar(sf.numChannels, buffer, phasor, 0) * amp.dbamp);
		}
	}

	prPlay {|lo, hi, amp = 1, rate = 1|
		if(lo == hi, {hi = sf.numFrames - lo});
		if(lo != hi, {
			play = this.player.play(target: group, args:[ \lo, lo, \hi, hi, \amp, amp, \rate, rate ]).register;
			responder = OSCFunc({ |msg|{ sfv.timeCursorPosition = msg[3] }.defer}, '/pointer', argTemplate: [play.nodeID]);
		});
	}

	prStop {
		if(play.isPlaying, {play.free});
	}

	recBufAlloc {/*|numChans = 2|*/
		recBuffer = Buffer.alloc(server, server.sampleRate * this.getDur, sf.numChannels);
		^recBuffer;
	}

	recBufFree {
		^recBuffer.free;
	}

	recBufSynth {
		^ {
			var audio = In.ar(this.outbus, sf.numChannels);
			var sync = In.ar(this.syncbus);
			var trig = ToggleFF.ar(sync);
			RecordBuf.ar(audio, recBuffer, run: trig, loop: 0) * EnvGen.ar(Env.cutoff(0.01), gate: trig, doneAction: 2);
			Out.ar(0, audio);
		}.play(target: group, addAction: 'addToTail')
	}

	recBufWrite {|path, headerFormat = "WAV", sampleFormat = "int24"|
		fork{
			recBuffer.write(path: path, headerFormat: headerFormat, sampleFormat: sampleFormat);
			server.sync;
			this.recBufFree;
			"recBuffer written to disk and freed from server. Reallocate by calling recBufAlloc before next recording".warn;
		}
	}
}


