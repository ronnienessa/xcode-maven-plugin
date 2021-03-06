/*
 * #%L
 * xcode-maven-plugin
 * %%
 * Copyright (C) 2012 SAP AG
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.sap.prd.mobile.ios.mios;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProjectHelper;

/**
 * Packages the Xcode project with all its resolved binary dependencies. I.e. this archive can be
 * unzipped and directly opened in Xcode. It uses the <code>zip</code> command for packaging. The
 * zip command is called with the <code>-y</code> options to preserver symbolic links and the
 * <code>-r</code> option to follow the paths recursively. The following files and folders get
 * packaged:
 * <ul>
 * <li>
 * <code>src/xcode/</code> (or the directory specified by the Maven parameter xcode.sourceDirectory
 * in you changed the default Xcode project location)</li>
 * <li><code>pom.xml</code></li>
 * <li><code>sync.info</code></li>
 * <li><code>target/bundles/</code></li>
 * <li><code>target/headers/</code></li>
 * <li><code>target/libs/</code></li>
 * <li><code>target/xcode-deps/</code></li>
 * </ul>
 * 
 * You can use the {@link #additionalArchivePaths} and the {@link #excludes}
 * 
 * If called from command line you have to call <code>mvn initialize</code> before in order to make
 * sure that all binary dependencies have been retrieved from the command line.
 * 
 * Please note that this goal is not part of the default lifecycle for xcode-lib and xcode-app
 * projects.
 * 
 * @goal package-xcodeproj
 * 
 */
public class XCodePackageXcodeprojMojo extends AbstractXCodeMojo
{


  public static final String XCODEPROJ_WITH_DEPS_CLASSIFIER = "xcodeproj-with-deps";

  /**
   * You can use this parameter to define additional paths that shall be packaged into the source
   * archive. If the path denotes a folder the folder content with all subfolders will be added. If
   * the path points to a file only the file will be added.
   * 
   * Plugin configuration example:
   * 
   * <pre>
   *   &lt;build>
   *     &lt;plugins>
   *       &lt;plugin>
   *         &lt;groupId>com.sap.prd.mobile.ios.mios&lt;/groupId>
   *         &lt;artifactId>xcode-maven-plugin&lt;/artifactId>
   *         &lt;extensions>true&lt;/extensions>
   *         &lt;configuration>
   *           &lt;additionalArchivePaths>
   *             &lt;param>src/docs&lt;/param>
   *             &lt;param>src/uml&lt;/param>
   *             ...
   *           &lt;/additionalArchivePaths>
   *          &lt;/configuration>  
   *        &lt;/plugin>
   *        ...
   *       &lt;/plugins>
   *     &lt;/build>
   *   &lt;/build>
   * </pre>
   * 
   * @parameter
   * @since 1.3.2
   */
  private List<String> additionalArchivePaths;

  /**
   * Specify files or file patterns to be excluded from the archive.
   * 
   * Configuration example:
   * 
   * <pre>
   *   &lt;build>
   *     &lt;plugins>
   *       &lt;plugin>
   *         &lt;groupId>com.sap.prd.mobile.ios.mios&lt;/groupId>
   *         &lt;artifactId>xcode-maven-plugin&lt;/artifactId>
   *         &lt;extensions>true&lt;/extensions>
   *         &lt;configuration>
   *           &lt;excludes>
   *             &lt;param>*.tmp&lt;/param>
   *             &lt;param>*&#47;tmp/*&lt;/param>
   *             ...
   *           &lt;/excludes>
   *          &lt;/configuration>  
   *        &lt;/plugin>
   *        ...
   *       &lt;/plugins>
   *     &lt;/build>
   *   &lt;/build>
   * </pre>
   * 
   * @parameter
   * @since 1.3.3
   */
  private List<String> excludes;


  /**
   * @component
   */
  private MavenProjectHelper projectHelper;


  @Override
  public void execute() throws MojoExecutionException, MojoFailureException
  {

    String xprojZipFileName = project.getArtifactId() + "-" + XCODEPROJ_WITH_DEPS_CLASSIFIER + ".zip";

    try {
      // we have to do this via zip command line call in order to be able to package symbolic links
      ArrayList<String> zipCmdCall = new ArrayList<String>();
      File targetFolder = new File(project.getBuild().getDirectory());
      if (!targetFolder.isDirectory()) {
        targetFolder.mkdirs();
      }
      String relativeTargetDirName = FileUtils.getRelativePath(project.getBuild().getDirectory(), project.getBasedir()
        .getAbsolutePath(), "/");
      String relativeSrcDirName = FileUtils.getRelativePath(FolderLayout.getSourceFolder(project).getAbsolutePath(),
            project.getBasedir().getAbsolutePath(), "/");

      Collections.addAll(zipCmdCall, "zip", "-r", "-y", "-q", relativeTargetDirName + "/" + xprojZipFileName);

      zipCmdCall.add(relativeSrcDirName); // src/xcode folder
      zipCmdCall.add("pom.xml");
      zipCmdCall.add("sync.info");
      zipCmdCall.add(relativeTargetDirName + "/bundles");
      zipCmdCall.add(relativeTargetDirName + "/headers");
      zipCmdCall.add(relativeTargetDirName + "/libs");
      zipCmdCall.add(relativeTargetDirName + "/xcode-deps");
      if (additionalArchivePaths != null) {
        zipCmdCall.addAll(additionalArchivePaths);
      }

      if (excludes != null && !excludes.isEmpty()) {
        zipCmdCall.add("-x");
        zipCmdCall.addAll(excludes);
      }

      getLog().info("Packaging the Xcode project with all its dependencies into the zip file " + xprojZipFileName);
      getLog().info("Executing: " + StringUtils.join(zipCmdCall, ' '));
      int exitCode = Forker.forkProcess(System.out, project.getBasedir(), zipCmdCall.toArray(new String[] {}));
      if (exitCode != 0) {
        throw new MojoExecutionException(
              "Could not package the Xcode project with all its dependencies into a zip file.");
      }
    }
    catch (IOException e) {
      throw new MojoExecutionException("Could not package the Xcode project with all its dependencies into a zip file.");
    }
    
    projectHelper.attachArtifact(project, "zip", XCODEPROJ_WITH_DEPS_CLASSIFIER, new File(project.getBuild()
      .getDirectory(), xprojZipFileName));
    

  }


}
