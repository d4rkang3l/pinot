/**
 * Copyright (C) 2014-2015 LinkedIn Corp. (pinot-core@linkedin.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.linkedin.pinot.core.operator.filter.predicate;

import java.util.ArrayList;
import java.util.List;

import com.linkedin.pinot.core.common.predicate.InPredicate;
import com.linkedin.pinot.core.segment.index.readers.Dictionary;


public class InPredicateEvaluator extends AbstractPredicateEvaluator {

  public InPredicateEvaluator(InPredicate predicate, Dictionary dictionary) {
    List<Integer> dictIds = new ArrayList<Integer>();
    final String[] inValues = predicate.getInRange();
    for (final String value : inValues) {
      final int index = dictionary.indexOf(value);
      if (index >= 0) {
        dictIds.add(index);
      }
    }
    matchingIds = new int[dictIds.size()];
    for (int i = 0; i < matchingIds.length; i++) {
      matchingIds[i] = dictIds.get(i);
    }
  }
}
