package org.aksw.conjure.cli.main;

import java.util.ArrayList;
import java.util.List;

import org.aksw.jena_sparql_api.http.repository.impl.HttpResourceRepositoryFromFileSystemImpl;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

@Parameters(separators = "=", commandDescription = "Show DCAT information")
public class CommandMain {
	@Parameter(names="--r", description="DCAT repo path")
	public String repoBase = HttpResourceRepositoryFromFileSystemImpl.getDefaultPath().toUri().toString();

	@Parameter(names="--w", description="Web base URL")
	public String webBase = "http://localhost/";
	
	@Parameter(description="Non option args")
	public List<String> nonOptionArgs = new ArrayList<>();

	@Parameter(names="--f", description="Input catalog/model file")
	public String inputModelFile;
	
	@Parameter(names = "--help", help = true)
	public boolean help = false;

	@Parameter(names="--m", description="Spark Master")
	public String sparkMaster = "local";

	@Parameter(names="--n", description="numThreads")
	public int numThreads = 2;
}
