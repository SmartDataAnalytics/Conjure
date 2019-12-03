package org.aksw.conjure.cli.main;

import java.util.ArrayList;
import java.util.List;

import org.aksw.jena_sparql_api.http.repository.impl.HttpResourceRepositoryFromFileSystemImpl;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(separators = "=", commandDescription = "Show DCAT information")
public class CommandMain {
	@Parameter(names="--r", description="DCAT repo path", help=false)
	public String repoBase = HttpResourceRepositoryFromFileSystemImpl.getDefaultPath().toUri().toString();

	@Parameter(names="--w", description="Web base URL", help=false)
	public String webBase = "http://localhost/";

	
	@Parameter(description="Non option args")
	public List<String> nonOptionArgs = new ArrayList<>();

	@Parameter(names="--f", description="Input catalog/model file", help=false)
	public String inputModelFile;
	
	@Parameter(names = "--help", help = true)
	public boolean help = false;
}
