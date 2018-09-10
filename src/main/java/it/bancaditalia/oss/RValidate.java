package it.bancaditalia.oss;

import java.io.File;
import java.util.Vector;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.rosuda.JRI.REXP;
import org.rosuda.JRI.Rengine;

/**
 * <p>
 * Goal that performs some initial checks on the package metadata.
 * </p>
 * 
 * <p>
 * Compliance requires passing tests enumerated in
 * <a href="https://cran.r-project.org/doc/manuals/r-devel/R-exts.html#The-DESCRIPTION-file">official documentation</a>
 * </p>
 */
@Mojo(name = "validate", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class RValidate extends AbstractRMojo
{
	/**
	 * You can skip the execution of the plugin if you need to. Its use is NOT RECOMMENDED, but quite convenient on
	 * occasion.
	 */
	@Parameter(property = "R.validate.skip", defaultValue = "false") private boolean skip;

	@SuppressWarnings("unchecked")
	@Override
	public void execute() throws MojoExecutionException
	{
		Log log = getLog();

		if (isSkip())
		{
			getLog().info("Skipping the execution.");
			return;
		}

		File description = new File(project.getBuild().getOutputDirectory() + File.separator + "DESCRIPTION");
		if (!description.exists())
		{
			log.error("DESCRIPTION file does not exists.");
			log.error("Remember to use the 'sources' goal.");
			throw new MojoExecutionException("DESCRIPTION file does not exists.");
		}

		Rengine engine = getEngine();
		
		synchronized (engine)
		{
			String statement = "tools:::.check_package_description('" + description.toString().replaceAll("\\\\", "\\\\") + "')";
			log.debug("Executing R statement: " + statement);
			REXP result = tryCatch(statement);
			for (REXP elem : (Vector<REXP>) result.asVector())
				log.error(elem.asString());
			if (result.asVector().size() > 0)
				throw new MojoExecutionException("Project metadata contains errors.");
		}
		
		log.info("Validation complete.");
	}

	/**
	 * @return {@link #skip}
	 */
	public boolean isSkip()
	{
		return skip;
	}

}
