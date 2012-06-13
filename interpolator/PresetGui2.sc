PresetGui2 : ObjectGui { 
	var tree, addButton;
	
	update { |model, what ... args|
		what.switch(
			\paramAdded, {
				//args[0] is a new Parameter.
				ParameterView(tree,args[0],model.parameters.size - 1);
			},
			\paramRemoved, {
				//args[0] is a Parameter.
				//args[1] is the id.
				// tree.removeItem(args[0].view.treeItem);
				tree.removeItem(tree.itemAt(args[1]));
			},
			\presetName, {
				tree.setProperty(\windowTitle,model.name);
			}
		);
	}

	gui {arg layout,bounds ... args;
		tree = TreeView().columns_(
			["Name", "", "Mapped", "Unmapped", "OSC","MIDI",""]
		).resize_(5);

		model.parameters.do{|i,j|
			ParameterView(tree,i,j);
		};

		addButton = tree.addItem([""]).setView(
			0,
			Button().states_([["Add"]]).action_({
				model.add(Parameter())
			})
		);
		tree.setProperty(\windowTitle,model.name);
		tree.onClose_({
			model.removeDependant(this);
		});
		tree.front;
	}

	background_ { |color|
		tree.background_(color);
	}

}
