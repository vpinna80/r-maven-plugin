package it.bancaditalia.oss;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.rosuda.JRI.REXP;
import org.rosuda.JRI.Rengine;

/**
 * Goal which runs R CMD check on an already built package.
 * 
 * It requires the project to already have packaged with the 'build' goal or by some other means.
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class RCheckMojo extends AbstractRMojo
{
	@Override
	public void execute() throws MojoExecutionException
	{
		Log log = getLog();

		log.info("Check started");
		checkRPackageVersion();
		setupDirectories();

		Rengine engine = getEngine();

		synchronized (engine)
		{
			String statement = "setwd('" + project.getBuild().getDirectory() + "')";
			log.debug("Executing R statement: " + statement);
			REXP res = tryCatch(statement);
			File packageArchive = new File(new File(project.getBuild().getDirectory()), project.getArtifactId() + "_" + checkRPackageVersion() + ".tar.gz");
			statement = "devtools::check_built(path = '" + packageArchive.toString().replaceAll("\\\\", "\\\\")  + "', quiet = T, " 
					+ "check_dir = '" + project.getBuild().getDirectory().replaceAll("\\\\", "\\\\") + "')";
			log.debug("Executing R statement: " + statement);
			res = tryCatch(statement);
			if (res == null)
				throw new MojoExecutionException("R internal error while invoking R CMD check");
			else if (res.asStringArray() != null)
			{
				for (String error : res.asStringArray())
					log.error(error);
				throw new MojoExecutionException("R internal error while invoking R CMD check");
			}
			else if (res.asString() != null)
				throw new MojoExecutionException("R internal error while invoking R CMD check: " + res.asString());
			else if (res.getType() != REXP.XT_NULL)
			{
				List<String> errors = new ArrayList<>(Arrays.asList(res.asVector().at("errors").asStringArray()));
				List<String> warnings = new ArrayList<>(Arrays.asList(res.asVector().at("warnings").asStringArray()));
				List<String> notes = new ArrayList<>(Arrays.asList(res.asVector().at("notes").asStringArray()));
				
				log.info("Check completed.");
	
				for (String error : errors)
					for (String line : error.split("\\r\\n|\\n"))
						log.error("        " + line);
				for (String warning : warnings)
					for (String line : warning.split("\\r\\n|\\n"))
						log.warn("        " + line);
				for (String note : notes)
					for (String line : note.split("\\r\\n|\\n"))
						log.info("        " + line);
	
				if (errors.size() > 0)
					throw new MojoExecutionException("Checking R package resulted in errors.");
			}
			else
				log.info("Check completed.");
		}
	}
}
