/**
 * Copyright (C) 2014-2016 LinkedIn Corp. (pinot-core@linkedin.com)
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

package com.linkedin.pinot.controller.helix.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.helix.HelixAdmin;
import org.apache.helix.ZNRecord;
import org.apache.helix.model.ExternalView;
import org.apache.helix.model.IdealState;
import org.apache.helix.store.zk.ZkHelixPropertyStore;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;
import com.linkedin.pinot.controller.helix.core.SegmentDeletionManager;
import junit.framework.Assert;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyList;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class SegmentDeletionManagerTest {
  final static String tableName = "table";
  final static String clusterName = "mock";

  HelixAdmin makeHelixAdmin() {
    HelixAdmin admin = mock(HelixAdmin.class);
    ExternalView ev = mock(ExternalView.class);
    IdealState is = mock(IdealState.class);
    when(admin.getResourceExternalView(clusterName, tableName)).thenReturn(ev);
    when(admin.getResourceIdealState(clusterName, tableName)).thenReturn(is);

    List<String> segmentsInIs = segmentsInIdealStateOrExtView();
    Map<String, String> dummy = new HashMap<>(1);
    dummy.put("someHost", "ONLINE");
    for (String segment : segmentsInIs) {
      when(is.getInstanceStateMap(segment)).thenReturn(dummy);
    }
    when(ev.getStateMap(anyString())).thenReturn(null);

    return admin;
  }

  ZkHelixPropertyStore<ZNRecord> makePropertyStore() {
    ZkHelixPropertyStore store = mock(ZkHelixPropertyStore.class);
    final List<String> failedSegs = segmentsFailingPropStore();
    /*
    for (String segment : failedSegs) {
      String propStorePath = ZKMetadataProvider.constructPropertyStorePathForSegment(tableName, segment);
      when(store.remove(propStorePath, AccessOption.PERSISTENT)).thenReturn(false);
    }
    List<String> successfulSegs = segmentsThatShouldBeDeleted();
    for (String segment : successfulSegs) {
      String propStorePath = ZKMetadataProvider.constructPropertyStorePathForSegment(tableName, segment);
      when(store.remove(propStorePath, AccessOption.PERSISTENT)).thenReturn(true);
    }
    */
    when(store.remove(anyList(), anyInt())).thenAnswer(new Answer<boolean[]>() {
      @Override
      public boolean[] answer(InvocationOnMock invocationOnMock)
          throws Throwable {
        List<String> propStoreList = (List<String>) (invocationOnMock.getArguments()[0]);
        boolean[] result = new boolean[propStoreList.size()];
        for (int i = 0; i < result.length; i++) {
          final String path = propStoreList.get(i);
          final String segmentId = path.substring((path.lastIndexOf('/') + 1));
          if (failedSegs.indexOf(segmentId) < 0) {
            result[i] = true;
          } else {
            result[i] = false;
          }
        }
        return result;
      }
    });

        when(store.exists(anyString(), anyInt())).thenReturn(true);
    return store;
  }

  List<String> segmentsThatShouldBeDeleted() {
    List<String> result = new ArrayList<>(3);
    result.add("seg1");
    result.add("seg2");
    result.add("seg3");
    return result;
  }

  List<String> segmentsInIdealStateOrExtView() {
    List<String> result = new ArrayList<>(3);
    result.add("seg11");
    result.add("seg12");
    result.add("seg13");
    return result;
  }

  List<String> segmentsFailingPropStore() {
    List<String> result = new ArrayList<>(3);
    result.add("seg21");
    result.add("seg22");
    result.add("seg23");
    return result;
  }

  @Test
  public void testBulkDeleteWithFailures() throws Exception {
    testBulkDeleteWithFailures(true);
    testBulkDeleteWithFailures(false);
  }

  public void testBulkDeleteWithFailures(boolean useSet) throws Exception {
    HelixAdmin helixAdmin =  makeHelixAdmin();
    ZkHelixPropertyStore<ZNRecord> propertyStore = makePropertyStore();
    FakeDeletionManager deletionManager = new FakeDeletionManager(helixAdmin, propertyStore);
    Collection<String> segments;
    if (useSet) {
      segments = new HashSet<String>();
    } else {
      segments = new ArrayList<String>();
    }

    segments.addAll(segmentsThatShouldBeDeleted());
    segments.addAll(segmentsInIdealStateOrExtView());
    segments.addAll(segmentsFailingPropStore());
    deletionManager.deleteSegmenetsFromPropertyStoreAndLocal(tableName, segments);

    Assert.assertTrue(deletionManager.segmentsToRetry.containsAll(segmentsFailingPropStore()));
    Assert.assertTrue(deletionManager.segmentsToRetry.containsAll(segmentsInIdealStateOrExtView()));
    Assert.assertTrue(deletionManager.segmentsRemovedFromStore.containsAll(segmentsThatShouldBeDeleted()));
  }

  @Test
  public void testAllFailed() throws Exception {
    testAllFailed(segmentsFailingPropStore());
    testAllFailed(segmentsInIdealStateOrExtView());
    List<String> segments = segmentsFailingPropStore();
    segments.addAll(segmentsInIdealStateOrExtView());
    testAllFailed(segments);
  }

  @Test
  public void allPassed() throws Exception {
    HelixAdmin helixAdmin =  makeHelixAdmin();
    ZkHelixPropertyStore<ZNRecord> propertyStore = makePropertyStore();
    FakeDeletionManager deletionManager = new FakeDeletionManager(helixAdmin, propertyStore);
    Set<String> segments = new HashSet<>();
    segments.addAll(segmentsThatShouldBeDeleted());
    deletionManager.deleteSegmenetsFromPropertyStoreAndLocal(tableName, segments);

    Assert.assertEquals(deletionManager.segmentsToRetry.size(), 0);
    Assert.assertEquals(deletionManager.segmentsRemovedFromStore.size(), segments.size());
    Assert.assertTrue(deletionManager.segmentsRemovedFromStore.containsAll(segments));
  }

  private void testAllFailed(List<String> segments) throws Exception {
    HelixAdmin helixAdmin =  makeHelixAdmin();
    ZkHelixPropertyStore<ZNRecord> propertyStore = makePropertyStore();
    FakeDeletionManager deletionManager = new FakeDeletionManager(helixAdmin, propertyStore);
    deletionManager.deleteSegmenetsFromPropertyStoreAndLocal(tableName, segments);

    Assert.assertTrue(deletionManager.segmentsToRetry.containsAll(segments));
    Assert.assertEquals(deletionManager.segmentsToRetry.size(), segments.size());
    Assert.assertEquals(deletionManager.segmentsRemovedFromStore.size(), 0);
  }

  public static class FakeDeletionManager extends SegmentDeletionManager {

    public Set<String> segmentsRemovedFromStore = new HashSet<>();
    public Set<String> segmentsToRetry = new HashSet<>();

    FakeDeletionManager(HelixAdmin helixAdmin, ZkHelixPropertyStore<ZNRecord> propertyStore) {
      super(null, helixAdmin, clusterName, propertyStore);
    }

    public void deleteSegmenetsFromPropertyStoreAndLocal(String tableName, Collection<String> segments) {
      super.deleteSegmentFromPropertyStoreAndLocal(tableName, segments, 0L);
    }

    @Override
    protected void removeSegmentFromStore(String tableName, String segmentId) {
      segmentsRemovedFromStore.add(segmentId);
    }
    @Override
    protected void deleteSegmentsWithDelay(final String tableName, final Collection<String> segmentIds,
        final long deletionDelaySeconds) {
      segmentsToRetry.addAll(segmentIds);
    }
  }
}
