/*******************************************************************************
 * (c) Copyright 2017 Hewlett Packard Enterprise Development LP Licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance with the License. You
 * may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *******************************************************************************/
package com.hp.hpl.loom.adapter.docker.distributed;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.hp.hpl.loom.adapter.AggregationUpdater;
import com.hp.hpl.loom.adapter.BaseAdapter;
import com.hp.hpl.loom.adapter.ConnectedItem;
import com.hp.hpl.loom.adapter.docker.distributed.realworld.HostManager;
import com.hp.hpl.loom.adapter.docker.distributed.realworld.Volume;
import com.hp.hpl.loom.adapter.docker.items.Relationships;
import com.hp.hpl.loom.adapter.docker.items.Types;
import com.hp.hpl.loom.adapter.docker.items.VolumeItem;
import com.hp.hpl.loom.adapter.docker.items.VolumeItemAttributes;
import com.hp.hpl.loom.exceptions.NoSuchItemTypeException;
import com.hp.hpl.loom.exceptions.NoSuchProviderException;
import com.hp.hpl.loom.model.Aggregation;
import com.hp.hpl.loom.model.CoreItemAttributes.ChangeStatus;

/***
 * Collects all volumes in all Hosts observed.
 */
public class VolumeItemUpdater extends AggregationUpdater<VolumeItem, VolumeItemAttributes, Volume> {

    private static final String VOLUME_DESCRIPTION = "Represents a docker volume";

    protected DockerDistributedCollector dockerCollector = null;

    /**
     * Constructs a VolumeItemUpdater.
     *
     * @param aggregation The aggregation this update will update
     * @param adapter The baseAdapter this updater is part of
     * @param DockerDistributedCollector The collector it uses
     *
     * @throws NoSuchItemTypeException Thrown if the ItemType isn't found
     * @throws NoSuchProviderException thrown if adapter is not known
     */
    public VolumeItemUpdater(final Aggregation aggregation, final BaseAdapter adapter,
            final DockerDistributedCollector dockerCollector) throws NoSuchItemTypeException, NoSuchProviderException {
        super(aggregation, adapter, dockerCollector);
        this.dockerCollector = dockerCollector;
    }

    /**
     * Each observed resource should have a way to be identified uniquely within the given adapter’s
     * domain and this is what should be returned here. This method is called to create the Item
     * logicalId.
     *
     * @return a unique way to identify a given resource (within the docker adapter). In the case of
     *         volume, it is the volume hash.
     */
    @Override
    protected String getItemId(final Volume argVolume) {
        int hash = argVolume.hashCode();
        return Integer.toString(hash);
    }

    /***
     * This must return a brand new Iterator every collection cycle giving access to all the
     * resources that AggregationUpdater is observing.
     *
     */
    @Override
    protected Iterator<Volume> getResourceIterator() {
        List<Volume> volumeList = HostManager.getInstance(adapter).getAllVolumes();

        return volumeList.iterator();
    }

    @Override
    protected Iterator<Volume> getUserResourceIterator(final Collection<Volume> data) {
        return data.iterator();
    }

    /**
     * This method should return an Item only set with its logicalId and ItemType.
     */
    @Override
    protected VolumeItem createEmptyItem(final String logicalId) {
        VolumeItem item = new VolumeItem(logicalId, itemType);
        return item;
    }

    /**
     * This should return a newly created CoreItemAttributes object based on data observed from the
     * resource.
     */
    @Override
    protected VolumeItemAttributes createItemAttributes(final Volume resource) {

        VolumeItemAttributes attr = new VolumeItemAttributes();

        attr.setPathOnHost(resource.getPath());
        // Added to allow multiple hosts
        attr.setHostId(resource.getHostId());

        attr.setItemId(getItemId(resource));
        attr.setItemDescription(VOLUME_DESCRIPTION);
        attr.setItemName(attr.getPathOnHost());
        return attr;
    }

    /***
     * This method returns a status value encoded as follows:
     *
     * <p>
     * <strong>CoreItemAttributes.Status.UNCHANGED</strong>:there are no changes detected between
     * the previous view (the CoreItemsAttributes argument) and the new one (the Resource argument).
     * The selection of the attributes actually compared is left entirely at the adapter writer’s
     * discretion: for instance, our AggregationUpdater for an OpenStack volume checks the value of
     * the "status" attribute only.
     * <p>
     * <strong>CoreItemAttributes.Status.CHANGED_IGNORE</strong>: some attributes have changed but
     * those changes do not impact any queries or relationships, i.e. the current aggregation cache
     * that Loom has built is still valid. This is in particular targeted at the update of
     * metrics-like attributes or any fast changing ones.
     * <p>
     * <strong>CoreItemAttributes.Status.CHANGED_UPDATE</strong>: the attributes that have changed
     * have an impact of queries, derived attributes or relationships. This means that Loom should
     * mark the related GroundedAggregation as dirty and invalidate any cached DerivedAggregations
     *
     * @param VolumeAttributes The Volume Item Attributes
     * @param resource The volume on docker-java API
     * @return
     */
    @Override
    protected ChangeStatus compareItemAttributesToResource(final VolumeItemAttributes volumeAttributes,
            final Volume resource) {

        ChangeStatus status;

        if (!volumeAttributes.getItemId().equals(Integer.toString(resource.hashCode()))) {
            status = ChangeStatus.CHANGED_UPDATE;
        } else {
            status = ChangeStatus.CHANGED_IGNORE;
        }

        return status;
    }

    /***
     * If the given resource is connected to another resource, then this method must set the itemId
     * of the connected Resource for a given relationship using the method
     * adapterItem.setRelationship(ItemTypeLocalId, connectedItemId) where ItemTypeLocalId is used
     * by the helper classes to name the relationship and derive the logicalId of the Item matching
     * the connected resource. ConnectedItem is an interface implemented by AdapterItem exposing the
     * few methods that should be used within the context of this method. *
     *
     * @param volumeItem the volume item that Items will be connected to
     * @param resource the docker-java API item, from where the connections will be extracted.
     */
    @Override
    protected void setRelationships(final ConnectedItem volumeItem, final Volume resource) {

        Map<Volume, List<String>> containersThatMountsVolume =
                HostManager.getInstance().locateHostByUID(resource.getHostId()).getVolumeMap();

        for (String containerId : containersThatMountsVolume.get(resource)) {
            volumeItem.setRelationshipWithType(adapter.getProvider(), Types.CONTAINER_TYPE_ID, containerId,
                    Relationships.MOUNTS_TYPE);
        }

    }
}
