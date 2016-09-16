/*
 * Copyright 2012-2016, the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flipkart.flux.guice.module;

import com.flipkart.flux.deploymentunit.DeploymentUnit;
import com.flipkart.flux.deploymentunit.DeploymentUnitClassLoader;
import com.flipkart.flux.deploymentunit.DeploymentUnitUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * A place for all DeploymentUnit related guice-foo
 */
public class DeploymentUnitModule extends AbstractModule {
    private static final Logger logger = LoggerFactory.getLogger(DeploymentUnitModule.class);

    @Override
    protected void configure() {
        bind(DeploymentUnitUtil.class).toProvider(DeploymentUnitUtilProvider.class).in(Singleton.class);
    }

    /**
     * Returns Map<deploymentUnitName,DeploymentUnit> of all the deployment units available //todo: this shows only deployment units available at boot time, need to handle dynamic deployments.
     */
    @Provides
    @Singleton
    @Named("deploymentUnits")
    public Map<String, DeploymentUnit> getAllDeploymentUnits(DeploymentUnitUtil deploymentUnitUtil) throws Exception {
        Map<String, DeploymentUnit> deploymentUnits = new ConcurrentHashMap<>();

        try {
            List<String> deploymentUnitNames = deploymentUnitUtil.getAllDeploymentUnitNames();
            CountDownLatch duCountDownLatch = new CountDownLatch(deploymentUnitNames.size());

            for (String deploymentUnitName : deploymentUnitNames) {
                new Thread(new DeploymentUnitLoader(deploymentUnits, duCountDownLatch, deploymentUnitName, deploymentUnitUtil)).start();
            }
            //wait till all the deployment units loaded
            duCountDownLatch.await();

        } catch (NullPointerException e) {
            logger.error("No deployment units found at location mentioned in configuration.yml - deploymentUnitsPath key");
        }
        return deploymentUnits;
    }

    /**
     * <code>DeploymentUnitLoader</code> creates a Deployment unit for a particular deployment unit name and puts it in deploymentUnits map for future use.
     */
    private class DeploymentUnitLoader implements Runnable {

        private Map<String, DeploymentUnit> deploymentUnits;

        private CountDownLatch duCountDownLatch;

        private String deploymentUnitName;

        private DeploymentUnitUtil deploymentUnitUtil;

        DeploymentUnitLoader(Map<String, DeploymentUnit> deploymentUnits, CountDownLatch duCountDownLatch, String deploymentUnitName, DeploymentUnitUtil deploymentUnitUtil) {
            this.deploymentUnits = deploymentUnits;
            this.duCountDownLatch = duCountDownLatch;
            this.deploymentUnitName = deploymentUnitName;
            this.deploymentUnitUtil = deploymentUnitUtil;
        }

        @Override
        public void run() {
            try {
                DeploymentUnitClassLoader deploymentUnitClassLoader = deploymentUnitUtil.getClassLoader(deploymentUnitName);
                Set<Method> taskMethods = deploymentUnitUtil.getTaskMethods(deploymentUnitClassLoader);
                deploymentUnits.put(deploymentUnitName, new DeploymentUnit(deploymentUnitName, deploymentUnitClassLoader, taskMethods));
            } catch (Exception e) {
                logger.error("Error occurred while loading Deployment Unit: {}", deploymentUnitName, e);
            } finally {
                //count down the latch irrespective of loading status of a particular deployment unit
                duCountDownLatch.countDown();
            }
        }
    }
}
