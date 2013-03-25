PresetInterpolatorServer : PresetInterpolator {
	var <synth, <bus;

	*new { arg model;
		model = model ?? {InterpolatorServer()};
		^super.newCopyArgs(model).init;
	}
	
	*load { |path|
		var e, interp;
		e = path.load;
		^super.newCopyArgs(
			InterpolatorServer(e.at(\points)[0].size)
		).init(e);
	}

	// used by .load
	// e should be structured like this:
	// (
	// points: [point,point,...],
	// cursor: cursor.saveable,
	// cursorPos: cusorPosition, (array)
	// presets: [preset.saveable, preset.saveable, ...]
	// colors: [color, color, ...]
	// )
	initWithEvent { |e|
		{
			model.server.bootSync;
			model.server.sync;
			model.cursor_(e.at(\cursorPos));
			//move point 0 (it is already there) and remove it from the event.
			model.movePoint(0, e.at(\points)[0]);
			e.at(\points).removeAt(0);
			model.movePoint(1, e.at(\points)[1]);
			e.at(\points).removeAt(1);
			// add all other points
			e.at(\points).do{|i|
				model.add(i);
			};
			// add parameters to cursor.
			// they will be added to other points as well (they are siblings).
			e.at(\cursor).at(\parameters).do({|i|
				var p;
				p = ParameterServer(i.name, i.spec, i.value, cursor);
				model.server.sync;
				p.netAddr_(i.netAddr);
				p.oscMess_(i.oscMess);
				p.sendMIDI_(i.sendMIDI);
				p.sendOSC_(i.sendOSC);
				cursor.add(p);
				model.server.sync;
			});
			// e.presets[0].parameters.do{|i|
			// 	presets[0].add(i);
			// };
			// name presets set their parameter values.
			presets.do{ |i,j|
				i.name_(e.at(\presets)[j].at(\name));
				i.parameters.do{|k,l|
					k.value_(e.at(\presets)[j].at(\parameters)[l].value);
				};
			};
			// set colors
			model.colors_(e.at(\colors));
			model.server.sync;
			this.buildSynthDef;
		}.forkIfNeeded;
	}

	init {|e|
		{
			mediator = PIMediator();
			model.server.sync;
			model.addDependant(this);
			cursor = PresetServer(nil, "Cursor", this);
			presets = List[];
			model.points.do{
				presets.add(Preset.newFromSibling(cursor, "Preset"));
			};
			presets.do{ |i|
				// make the interpolator refresh the weights when a preset is
				// modified.
				i.addDependant(this);
			};
			this.initActions;
			model.server.sync;
			this.buildSynthDef;
			model.server.sync;
			e.notNil.if({this.initWithEvent(e)});
		}.forkIfNeeded;
	}

	buildSynthDef{
		{
			synth.free;
			bus.free;
			bus = Bus.control(model.server, cursor.parameters.size);
			model.server.sync;
			SynthDef(
				"presetInt_%_%".format(model.points.size, cursor.parameters.size),
				{
					arg out=0, in=0;
					var weights, siblingValues, cursorValues;
					weights = In.kr(in, model.points.size);
					siblingValues = 0 ! model.points.size;
					cursorValues = 0 ! cursor.parameters.size;
					cursor.parameters.do({|param,i|
						presets.do({|pset,j|
							siblingValues[j] = In.kr(pset.parameters[i].bus, 1);
						});
						cursorValues[i] = siblingValues.wmean(weights);
					});
					Out.kr(out, cursorValues)
				}
			).add;
			model.server.sync;
			synth = Synth.after(
				model.weightsSynth,
				"presetInt_%_%".format(model.points.size, cursor.parameters.size),
				[\out, bus, \in, model.weightsBus]
			);
			// Send the unmapped values of parameters the "ControlSpec Synths".
			model.server.sync;
			cursor.parameters.do({|i,j|
				i.synth.map(\in, bus.index + j);
			});
		}.forkIfNeeded;
	}

	free {
		model.free;
		synth.free;
		bus.free;
		cursor.free;
	}
	
	initActions {
		actions = IdentityDictionary[
			\weights -> {|model, what, interpoints, weights|
				cursor.parameters.do{ |i,j|
					//to each parameters of current Preset
					i.value_( //assign the value
						//of the weighted mean of values
						// of parameters of other presets
						presets.collect({|i|
							i.parameters[j].value
						}).wmean(weights));
				};
			},
			\pointAdded -> {|interpolator, what, point|
				presets.add(Preset.newFromSibling(cursor));
				presets.last.addDependant(this);
				this.changed(\presetAdded, presets.last);
				// this.buildSynthDef;
			},
			\kdtreeRebuilt -> {
				this.buildSynthDef;
			},
			\pointDuplicated -> {|interpolator, what, point, pointId|
				var paramValues;
				paramValues = presets[pointId].parameters.collect(_.value);
				model.add(point * 1.01);
				presets.last.parameters.do{ |i,j|
					i.value_(paramValues[j].value);
				};
				// this.buildSynthDef;
				this.changed(\presetAdded, presets.last);
			},
			\cursorDuplicated ->{|interpolator, what, point, pointId|
				var paramValues;
				paramValues = cursor.parameters.collect(_.value);
				model.add(point);
				presets.last.parameters.do{ |i,j|
					i.value_(paramValues[j].value);
				};
				// this.buildSynthDef;
				this.changed(\presetAdded, presets.last);
			},
			\pointRemoved -> {|interpolator, what, i|
				presets.removeAt(i);
				mediator.removePreset(presets[i]);
				this.changed(\presetRemoved, i);
			},
			\makeCursorGui -> {|model, what|
				cursor.gui.background_(Color.clear);
			},
			\makePointGui -> {|model, what, point, color|
				presets[point].gui.background_(color);
			},
			\paramValue -> {|preset, what, param, paramId, val|
				model.moveAction.value;
			},
			\paramRemoved -> { |model|
				// do this only once (not for every point/preset)
				(model === presets[0]).if({
					this.buildSynthDef;
				})
			},
			\paramAdded -> { |model|
				// do this only once (not for every point/preset)
				(model === presets[0]).if({
					this.buildSynthDef;
				})
			},
			\presetName -> {|preset, what, name|
				this.changed(
					\presetName, presets.indexOf(preset), name
				);
			},
			\attachedPoint -> {|interpolator, what, point|
				this.changed(\attachedPoint, point);
			}
		];
	}
	
	// gui stuff
	interpolatorGui { arg  ... args;
		^InterpolatorServerGui.new(model).performList(\gui,args);
	}

	namesGui { arg  ... args;
		^PresetInterpolatorGui.new(this).performList(\gui,args);
	}
}