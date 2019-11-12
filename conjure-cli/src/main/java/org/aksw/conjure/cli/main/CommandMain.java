package org.aksw.conjure.cli.main;

import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(separators = "=", commandDescription = "Show DCAT information")
public class CommandMain {
	@Parameter(description="Non option args")
	protected List<String> nonOptionArgs = new ArrayList<>();

	@Parameter(names="--f", description="Input catalog/model file", help=false)
	protected String inputModelFile;
	
	@Parameter(names = "--help", help = true)
	protected boolean help = false;
}
