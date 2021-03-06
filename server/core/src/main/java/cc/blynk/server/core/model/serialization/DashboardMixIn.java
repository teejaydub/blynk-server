package cc.blynk.server.core.model.serialization;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * User who see shared dashboard should not see authentification data of original user
 *
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 08.12.16.
 */
public abstract class DashboardMixIn {

    @JsonIgnore
    public String sharedToken;

}
