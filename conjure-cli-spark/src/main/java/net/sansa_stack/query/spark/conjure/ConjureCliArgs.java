package net.sansa_stack.query.spark.conjure;

import org.aksw.conjure.cli.main.CommandMain;

import com.beust.jcommander.JCommander;

/**
 * Bean that holds Conjure JCommander CLI information
 * 
 * @author raven
 *
 */
public class ConjureCliArgs {
	protected JCommander jcommander;
	protected CommandMain cm;
	
	public ConjureCliArgs(JCommander jcommander, CommandMain cm) {
		super();
		this.jcommander = jcommander;
		this.cm = cm;
	}
	
	public JCommander getJcommander() {
		return jcommander;
	}

	public CommandMain getCm() {
		return cm;
	}

	public static ConjureCliArgs parse(String[] args) {
		CommandMain cm = new CommandMain();

		JCommander jc = new JCommander.Builder()
	    	  .addObject(cm)
	    	  .build();

		jc.parse(args);

//	    if (cm.help) {
//	      jc.usage();
//	      return ;
//	    }
		
		ConjureCliArgs result = new ConjureCliArgs(jc, cm);
		return result;
	}
}
