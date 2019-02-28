package cc.blynk.server.core.model.auth;

import cc.blynk.server.core.model.serialization.JsonParser;

/* A Subscription holds data about a user's permissions to use the app.
   Permission can be granted or revoked independently, while still keeping the user's data intact.
   The user can read this data via the Profile, but it's only modifiable via the Admin interface.
*/

public class Subscription {

    // True iff the subscription is valid, false if not.
    // Just the predicate needed by Blynk to determine what to do with a device;
    // the rest of the business logic around subscriptions goes either on your provider,
    // or on another CRM or bridge server.
    public boolean isActive;

    // True if this user is allowed to log in as other users (for providing support).
    // Enables the 'masquerade' API.
    public boolean canMasquerade;

    // Additional JSON data that can be used to store whatever's necessary to link this subscription to a provider.
    public String metadata;

    public void update(Subscription other) {
        isActive = other.isActive;
        canMasquerade = other.canMasquerade;
        metadata = other.metadata;
    }

    @Override
    public String toString() {
        return JsonParser.toJson(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Subscription subscription = (Subscription) o;

        if (isActive != subscription.isActive) {
            return false;
        }
        if (canMasquerade != subscription.canMasquerade) {
            return false;
        }
        if (metadata != subscription.metadata) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = isActive ? 1 : 0;
        result += canMasquerade ? 2 : 0;
        result = 31 * result + (metadata != null ? metadata.hashCode() : 0);
        return result;
    }
}
