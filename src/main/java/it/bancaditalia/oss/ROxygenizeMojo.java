package it.bancaditalia.oss;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.rosuda.JRI.Rengine;

/**
 * Goal which runs <code>devtools::document.</code>
 */
@Mojo(name = "document", defaultPhase = LifecyclePhase.COMPILE)
public class ROxygenizeMojo extends AbstractRMojo
{
	/**
	 * A comma-separated list of unquoted roclet names to be passed to ROxygen.
	 */
	@Parameter(defaultValue = "") String roclets;

	@Override
	public void execute() throws MojoExecutionException
	{
		Log log = getLog();

		log.info("ROxygenize started.");
		checkRPackageVersion();
		setupDirectories();

		if (roclets != null)
			if (roclets.isEmpty())
				roclets = null;
			else
			{
				StringBuilder rocletArray = new StringBuilder();
				rocletArray.append("c(");
				boolean first = true;
				for (String roclet : roclets.split(","))
				{
					if (!first)
						rocletArray.append(", ");
					rocletArray.append("'").append(roclet).append("'");

					first = false;
				}
				rocletArray.append(")");
				roclets = rocletArray.toString();
			}

		Rengine engine;

		synchronized (engine = getEngine())
		{
			String statement = "setwd('" + project.getBuild().getDirectory() + "')";
			log.debug("Executing R statement: " + statement);
			engine.eval(statement);
			statement = "devtools::document(pkg = as.package('" + project.getBuild().getOutputDirectory() + "'), clean = T"
					+ (roclets != null ? ", roclets = " + roclets : "") + ")";
			log.debug("Executing R statement: " + statement);
			tryCatch(statement);
		}
		
		log.info("ROxygenize completed.");
	}
}
