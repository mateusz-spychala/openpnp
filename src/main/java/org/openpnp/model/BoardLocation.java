/*
 * Copyright (C) 2011 Jason von Nieda <jason@vonnieda.org>
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */

package org.openpnp.model;

import java.awt.geom.AffineTransform;
import java.util.HashMap;
import java.util.Map;

import org.openpnp.model.Board.Side;
import org.openpnp.model.Placement.Type;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.core.Commit;

public class BoardLocation extends AbstractModelObject {
    @Element
    private Location location;
    
    @Attribute
    private Side side = Side.Top;
    private Board board;

    @Attribute
    private String boardFile;

    @Attribute(required = false)
    private String panelId = new String("Panel1"); // UI doesn't have a way to specify multiple
                                                    // panels at this point

    @Attribute(required = false)
    private String panelPos = new String("---"); 
    
    @Attribute(required = false)
    private boolean checkFiducials;

    @Attribute(required = false)
    private boolean enabled = true;

    @ElementMap(required = false)
    private Map<String, Boolean> placed = new HashMap<>();

    /**
     * Important note: The placement transform is in Millimeters no matter what the source
     * units are.
     */
    private AffineTransform placementTransform;

    BoardLocation() {
        setLocation(new Location(LengthUnit.Millimeters));
    }

    // Copy constructor needed for deep copy of object.
    public BoardLocation(BoardLocation obj) {
        this.location = obj.location;
        this.side = obj.side;
        this.board = obj.board;
        this.boardFile = obj.boardFile;
        this.panelId = obj.panelId;
        this.checkFiducials = obj.checkFiducials;
        this.enabled = obj.enabled;
        this.placed = obj.placed;
    }

    public BoardLocation(Board board) {
        this();
        setBoard(board);
    }

    @SuppressWarnings("unused")
    @Commit
    private void commit() {
        setLocation(location);
        setBoard(board);
    }

    public Location getLocation() {
        return location;
    }

	public void setLocation(Location location) {
		Location oldValue = this.location;
		this.location = location;
		firePropertyChange("location", oldValue, location);

		// If the location (X, Y or Rotation) is changing it is not possible
		// the placement transform is still valid, so clear it.
		boolean resetTransform = false;

		if (oldValue == null) {
			resetTransform = true;
		} else {
			if (this.location.getX() != (oldValue.getX())
					|| this.location.getY() != (oldValue.getY())
					|| this.location.getRotation() != oldValue.getRotation()) {
				resetTransform = true;
			}
		}

		if (resetTransform) {
			setPlacementTransform(null);
		}
	}

    public Side getSide() {
        return side;
    }
    
    public int getTotalActivePlacements(){
    	if (board == null) {
    		return 0;
    	}
    	int counter = 0;
    	for(Placement placement : board.getPlacements()) {
    		if (placement.getSide() == getSide()
    		        && placement.getType() == Type.Placement
    		        && placement.isEnabled()) {
    				counter++;
        	}
    	}
    	return counter;
    }
    
    public int getActivePlacements() {
    	if (board == null) {
    		return 0;
    	}
    	int counter = 0;
	    for(Placement placement : board.getPlacements()) {
            if (placement.getSide() == getSide()
                    && placement.getType() == Type.Placement
                    && placement.isEnabled()
                    && !getPlaced(placement.getId())) {
                    counter++;
            }
        }
    	return counter;
    }

    public void setSide(Side side) {
        Object oldValue = this.side;
        this.side = side;
        firePropertyChange("side", oldValue, side);
    }

    public Board getBoard() {
        return board;
    }

    public void setBoard(Board board) {
        Board oldValue = this.board;
        this.board = board;
        firePropertyChange("board", oldValue, board);
    }

    String getBoardFile() {
        return boardFile;
    }

    void setBoardFile(String boardFile) {
        this.boardFile = boardFile;
    }

    public String getPanelId() {
        return panelId;
    }

    public void setPanelId(String id) {
        String oldValue = this.panelId;
        this.panelId = id;
        firePropertyChange("panelId", oldValue, panelId);
    }

    public String getPanelPos() {
        return panelPos;
    }

    public void setPanelPos(String pos) {
        String oldValue = this.panelPos;
        this.panelPos = pos;
        firePropertyChange("panelPos", oldValue, panelPos);
    }

    public boolean isCheckFiducials() {
        return checkFiducials;
    }

    public void setCheckFiducials(boolean checkFiducials) {
        boolean oldValue = this.checkFiducials;
        this.checkFiducials = checkFiducials;
        firePropertyChange("checkFiducials", oldValue, checkFiducials);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        boolean oldValue = this.enabled;
        this.enabled = enabled;
        firePropertyChange("enabled", oldValue, enabled);
    }

    public void setPlaced(String placementId, boolean placed) {
        this.placed.put(placementId, placed);
        firePropertyChange("placed", null, this.placed);
    }

    public boolean getPlaced(String placementId) {
        if (placed.containsKey(placementId)) {
            return placed.get(placementId);
        } 
        else {
            return false;
        }
    }
    
    public void clearAllPlaced() {
        this.placed.clear();
        firePropertyChange("placed", null, this.placed);
    }
    
    public AffineTransform getPlacementTransform() {
        return placementTransform;
    }

    public void setPlacementTransform(AffineTransform placementTransform) {
        Object oldValue = this.placementTransform;
        this.placementTransform = placementTransform;
        firePropertyChange("placementTransform", oldValue, placementTransform);
    }

    @Override
    public String toString() {
        return String.format("board (%s), location (%s), side (%s)", boardFile, location, side);
    }
}
