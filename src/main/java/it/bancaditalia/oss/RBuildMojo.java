package it.bancaditalia.oss;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.rosuda.JRI.REXP;
import org.rosuda.JRI.Rengine;

/**
 * Goal which runs R CMD BUILD.
 */
@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE)
public class RBuildMojo extends AbstractRMojo
{
	@Override
	public void execute() throws MojoExecutionException
	{
		Log log = getLog();
		
		log.info("Build started");
		checkRPackageVersion();
		setupDirectories();
		
		Rengine engine;
		
		synchronized (engine = getEngine())
		{
			String statement = "setwd('" + project.getBuild().getDirectory() + "')";
			log.debug("Executing R statement: " + statement);
			REXP res = engine.eval(statement);
			statement = "devtools::build(pkg = as.package('" + project.getBuild().getOutputDirectory() + "'), quiet = T, "
					+ "path = '" + project.getBuild().getDirectory() + "')";
			log.debug("Executing R statement: " + statement);
			res = tryCatch(statement);
			if (res == null)
				throw new MojoExecutionException("R internal error while invoking R CMD build");
			else if (attachArtifact)
			{
				File artifact = new File(res.asString());
				log.info("Added artifact " + artifact + " to project" + (classifier == null ? "" : " with classifier " + classifier) + ".");
				mavenProjectHelper.attachArtifact(project, "tar.gz", classifier, artifact);
			} 
			else if (classifier != null)
				log.warn("Classifier specified with artifact attachment disabled.");
		}
	}
}
