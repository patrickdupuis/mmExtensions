SpongeGui : ObjectGui {
	var <>actions, listInactive, listActive, viewActive, viewInactive,
	prefixView, portView, addrView;

	*new { arg model;
		var new;
		new = super.new;
		new.model_(model);
		^new.init;
	}

	init {
		this.prUpdateLists;
		actions = IdentityDictionary[
			\featureActivated -> {|model, what, featureDef|
				this.prUpdateLists;
				viewActive.items_(listActive);
				viewInactive.items_(listInactive);
			},
			\featureDeactivated -> {|model, what, featureDef|
				this.prUpdateLists;
				viewActive.items_(listActive);
				viewInactive.items_(listInactive);
			}

		];
	}

	prUpdateLists {
		listActive = model.features.collect({ |i| i.name });
		listInactive = Sponge.featureList.collect(
			{ |i| i[\name] } ).removeAll(listActive);
	}

	update { arg model, what ... args;
		var action;
		action = actions.at(what);
		if (action.notNil, {
			action.valueArray(model, what, args);
		});
	}

	gui { arg ... args;
		var w;
		w = Window("A Sponge GUI");
		// w = ScrollView(x, Rect(0,0,x.bounds.width, x.bounds.height));
		w.layout_(QVLayout());
		w.layout.add(
			QHLayout(
				StaticText(w).string_(
					"Sponge" + model.portName
				).align_(\center).font_(Font.default.size_(24))
			)
		);
		w.layout.add(
			QHLayout(
				StaticText(w).string_("OSC Prefix").align_(\right).minWidth_(80),
				prefixView = TextField(w).string_("/sponge/01"),
				Button(w).states_([["Set For All Features"]]).action_({
					model.setOSCprefix(prefixView.value);
				})
			)
		);
		w.layout.add(
			QHLayout(
				StaticText(w).string_("OSC Address").align_(\right).minWidth_(80),
				addrView = TextField(w).string_(NetAddr.localAddr.hostname),
				Button(w).states_([["Set For All Features"]]).action_({
					model.setOSCaddr(addrView.value);
				})
			)
		);
		w.layout.add(
			QHLayout(
				StaticText(w).string_("OSC Port").align_(\right).minWidth_(80),
				portView = TextField(w).string_(NetAddr.localAddr.port),
				Button(w).states_([["Set For All Features"]]).action_({
					model.setOSCport(portView.value.asInteger);
				})
			)
		);
		w.layout.add(
			QHLayout(
				StaticText(w).string_("Available Features"),
				StaticText(w).string_("Active Features")
			);
		);
		w.layout.add(
			QHLayout(
				viewInactive = ListView(w).items_(listInactive).mouseDownAction_({
					|view, x, y, modifiers, buttonNumber, clickCount|
					(buttonNumber == 0 and: clickCount == 2).if {
						model.activateFeature(view.items[view.value]);
					};
				}),
				viewActive = ListView(w).items_(listActive).mouseDownAction_({
					|view, x, y, modifiers, buttonNumber, clickCount|
					(buttonNumber == 0 and: clickCount == 2).if {
						model.features[
							model.featureNames.indexOf(view.items[view.value])
						].gui;
					};
				}).keyDownAction_({ |view, key|
					((key == 8.asAscii) or: (key == 127.asAscii)).if{
						model.deactivateFeature(view.items[view.value]);
					}
				})
			);
		);
		w.front;
	}

}
