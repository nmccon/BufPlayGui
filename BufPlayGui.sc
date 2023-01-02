BufPlayGui {
	var server, <>group, <>outbus;
	var win, sfv, open, boxes, controlView, controlBtn;
	var volKnob, volNum, rateKnob, rateNum;
	var buffer, player, play, responder;
	var sf;
	var dbSpec, rateSpec;
	var stringCol, backCol;

	*new { |server, group, outbus|
		var serverWarning = false;
		if( Server.default.serverRunning.not {
			"Server not running".warn;
			serverWarning = true
		});

		^super.newCopyArgs(server, group, outbus).prInitGUI;
	}

	prInitGUI {
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
				32, { if(play.isPlaying, { this.prStop }, { this.prPlay(posData[0], posData[1], dbSpec.map(volKnob.value), rateSpec.map(rateKnob.value)) } ) },
				122, {
					view.zoomToFrac(posData[1] - posData[0]);
					view.scrollTo (posData[0]);
					view.scroll (posData[0]);
				},
				90, {
					view.zoom(10);
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
			rateNum.value_(rateSpec.map(kn.value));
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
		);

		//make volume and playback rate visible
		controlBtn = Button()
		.canFocus_(false)
		.states_([["Controls", stringCol, Color.clear], ["Hide", stringCol, Color.clear]])
		.action_({|btn|
			controlView.visible = btn.value.asBoolean;
		});

		//GUI window
		win = Window("", Rect(906.0, 686.0, 611.0, 263.0)).front.background_(backCol)
		.layout_(GridLayout.rows(
			[ [open, columns: 1], nil, nil, nil, [controlBtn, columns: 1]],
			[ [sfv, columns: 6] ],
			[ [boxes[0], columns: 2, ],[boxes[1], columns: 2, ], [boxes[2], columns: 2, ]],
			[ [controlView, columns: 4]]
		))
		.onClose_({buffer.free; play = nil});
	}

	prLoadBuf { |buf|
		{
			buffer = Buffer.read(server, buf);
			server.sync;
			sf.close;
		}.fork;
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
		boxes[2].value_(dur);
	}

	getStartFrame {
		^ boxes[0].value
	}

	getEndFrame {
		^ boxes[1].value
	}

	getDur {
		^ boxes[2].value
	}

	getDurFrames {
		^ boxes[1].value - boxes[0].value
	}

	getBufnum {
		^buffer.bufnum
	}

	getSynthName {
		^ play.defName
	}

	getNodeID {
		^ play.nodeID
	}

	player {
		^{	|lo = 0, hi = 1, amp = 1, rate = 1|
			var phasor;
			phasor = Phasor.ar(0, rate * BufRateScale.kr(buffer), lo * BufFrames.kr(buffer), hi * BufFrames.ir(buffer));
			SendReply.kr(Impulse.kr(50), '/pointer', [A2K.kr(phasor)]);
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

}

/*
TODO
send trigger each time Phasor resets/loop starts HPZ1- sync with other processes later in signal path https://scsynth.org/t/triggering-envelope-with-phasor/6093/4
keyboard shortcuts e.g. H for show hide controls
possibly add envelope view for creating volume envs on the view using StackLayout? - hard!
time grid e.g. SFPlayer - even harder!
maybe a random button to select audio file at random from a folder
filter section in the control window
call playfunc when changing selection instead of replaying every new selection
consider using PlayBufCF??
mono option - useful for ATK HoaEncodeDirection
move varibles into initGUI method
MIDI learn function for controls
more control options
addToHead/tail option for group
*/