/*
 * Copyright 2023 opentelemetry-exporters-pack project
 * 
 * Website: https://github.com/lambdaprime/opentelemetry-exporters-pack
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package id.opentelemetry.exporters;

public interface ExportSchema {
    String METRIC_NAME = "METRIC_NAME";
    String START_TIME = "START_TIME";
    String END_TIME = "END_TIME";
    String VALUE = "VALUE";
    String COUNT = "COUNT";
    String SUM = "SUM";
    String MIN = "MIN";
    String MAX = "MAX";
    String METRIC_TYPE = "METRIC_TYPE";
    String AVG = "AVG";
    String SCOPE_NAME = "SCOPE_NAME";
    String SCOPE_VERSION = "SCOPE_VERSION";
    String SCOPE_SCHEMA = "SCOPE_SCHEMA";
}
