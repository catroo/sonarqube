/*
 * SonarQube
 * Copyright (C) 2009-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.app;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import org.apache.catalina.Context;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.process.ProcessProperties;
import org.sonar.process.Props;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TomcatContextsTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  Tomcat tomcat = mock(Tomcat.class);
  Properties props = new Properties();

  @Before
  public void setUp() throws Exception {
    props.setProperty(ProcessProperties.PATH_DATA, temp.newFolder("data").getAbsolutePath());
    when(tomcat.addWebapp(anyString(), anyString())).thenReturn(mock(StandardContext.class));
  }

  @Test
  public void configure_root_webapp() throws Exception {
    props.setProperty("foo", "bar");
    StandardContext context = mock(StandardContext.class);
    when(tomcat.addWebapp(anyString(), anyString())).thenReturn(context);

    new TomcatContexts().configure(tomcat, new Props(props));

    // configure webapp with properties
    verify(context).addParameter("foo", "bar");
  }

  @Test
  public void configure_rails_dev_mode() {
    props.setProperty("sonar.web.dev", "true");
    Context context = mock(Context.class);

    new TomcatContexts().configureRails(new Props(props), context);

    verify(context).addParameter("jruby.max.runtimes", "3");
    verify(context).addParameter("rails.env", "development");
  }

  @Test
  public void configure_rails_production_mode() {
    props.setProperty("sonar.web.dev", "false");
    Context context = mock(Context.class);

    new TomcatContexts().configureRails(new Props(props), context);

    verify(context).addParameter("jruby.max.runtimes", "1");
    verify(context).addParameter("rails.env", "production");
  }

  @Test
  public void create_dir_and_configure_static_directory() throws Exception {
    File dir = temp.newFolder();
    dir.delete();

    new TomcatContexts().addStaticDir(tomcat, "/deploy", dir);

    assertThat(dir).isDirectory().exists();
    verify(tomcat).addWebapp("/deploy", dir.getAbsolutePath());
  }

  @Test
  public void cleanup_static_directory_if_already_exists() throws Exception {
    File dir = temp.newFolder();
    FileUtils.touch(new File(dir, "foo.txt"));

    new TomcatContexts().addStaticDir(tomcat, "/deploy", dir);

    assertThat(dir).isDirectory().exists();
    assertThat(dir.listFiles()).isEmpty();
  }

  @Test
  public void fail_if_static_directory_can_not_be_initialized() throws Exception {
    File dir = temp.newFolder();
    expectedException.expect(IllegalStateException.class);
    expectedException.expectMessage("Fail to create or clean-up directory " + dir.getAbsolutePath());

    TomcatContexts.Fs fs = mock(TomcatContexts.Fs.class);
    doThrow(new IOException()).when(fs).createOrCleanupDir(any(File.class));

    new TomcatContexts(fs).addStaticDir(tomcat, "/deploy", dir);

  }
}