/*******************************************************************************
 * Copyright 2016 JSL Solucoes LTDA - https://jslsolucoes.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.jslsolucoes.nginx.admin.repository.impl;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.jdbc.Work;

import com.jslsolucoes.nginx.admin.annotation.Application;
import com.jslsolucoes.nginx.admin.repository.ConfigurationRepository;
import com.jslsolucoes.nginx.admin.repository.DatabaseRepository;

@RequestScoped
public class DatabaseRepositoryImpl implements DatabaseRepository {
	
	private Session session;
	private ConfigurationRepository configurationRepository;
	private Properties properties;

	public DatabaseRepositoryImpl() {
	}

	@Inject
	public DatabaseRepositoryImpl(@Application Properties properties, Session session,
			ConfigurationRepository configurationRepository) {
		this.properties = properties;
		this.session = session;
		this.configurationRepository = configurationRepository;
	}

	@Override
	public void installOrUpgrade() throws HibernateException, IOException {
		AtomicInteger installed = new AtomicInteger(installed());
		while (installed.get() < actual()) {
			Arrays.asList(resource("/sql/" + installed.incrementAndGet() + ".sql").split(";")).stream()
					.filter(StringUtils::isNotEmpty).forEach(statement -> {
						session.doWork(new Work() {
							@Override
							public void execute(Connection connection) throws SQLException {
								try {
									PreparedStatement preparedStatement = connection.prepareStatement(statement);
									preparedStatement.executeUpdate();
									preparedStatement.close();
								} catch (SQLException sqlException) {
									throw new RuntimeException(sqlException);
								}
							}
						});
					});
		}
	}

	private String resource(String path) throws IOException {
		return IOUtils.toString(getClass().getResourceAsStream(path), "UTF-8");
	}

	public Integer installed(){
		try {
			return configurationRepository.integer(ConfigurationType.DB_VERSION);
		} catch (Exception exception) {
			return 0;
		}
	}
	
	public Integer actual(){
		return Integer.valueOf(properties.getProperty("db.version"));
	}

	@Override
	public Boolean installOrUpgradeRequired() {
		return installed() != actual();
	}
}
