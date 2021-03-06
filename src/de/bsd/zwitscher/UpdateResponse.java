package de.bsd.zwitscher;

import android.view.View;
import android.widget.TextView;
import twitter4j.Status;
import twitter4j.StatusUpdate;

/**
 * Response object for any update sent to the
 * server.
 *
 * @author Heiko W. Rupp
 */
public class UpdateResponse {

    StatusUpdate update;
    long id;
    String message;
    boolean success;
    UpdateType updateType;
    Status status;
    View view;
    String origMessage;

    public UpdateResponse(UpdateType updateType, boolean success, String message) {
        this.message = message;
        this.success = success;
        this.updateType = updateType;
    }

    public UpdateResponse(UpdateType updateType, StatusUpdate update) {
        this.update = update;
        this.updateType = updateType;
    }

    public UpdateResponse(UpdateType updateType, Status status) {
        this.updateType = updateType;
        this.status = status;
    }

    public UpdateResponse(UpdateType updateType, long id) {
        this.updateType = updateType;
        this.id = id;
    }

    public UpdateResponse(UpdateType updateType, View view, String url) {
        this.updateType = updateType;
        this.view = view;
        this.message = url;
    }

    public UpdateType getUpdateType() {
        return updateType;
    }

    public StatusUpdate getUpdate() {
        return update;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return success;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setUpdate(StatusUpdate update) {
        this.update = update;
    }

    public void setSuccess() {
        success = true;
    }

    public void setFailure() {
        success = false;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getOrigMessage() {
        return origMessage;
    }

    public void setOrigMessage(String origMessage) {
        this.origMessage = origMessage;
    }
}
