package net.sf.openrocket.rocketcomponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import net.sf.openrocket.l10n.Translator;
import net.sf.openrocket.startup.Application;
import net.sf.openrocket.util.BugException;
import net.sf.openrocket.util.Coordinate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BoosterSet extends AxialStage implements FlightConfigurableComponent, OutsideComponent {
	
	private static final Translator trans = Application.getTranslator();
	private static final Logger log = LoggerFactory.getLogger(BoosterSet.class);
	
	private FlightConfigurationImpl<StageSeparationConfiguration> separationConfigurations;
	
	private double angularPosition_rad = 0;
	private double radialPosition_m = 0;
	
	private int count = 2;
	private double angularSeparation = Math.PI;
	
	public BoosterSet() {
		this.relativePosition = Position.BOTTOM;
		
	}
	
	@Override
	public String getComponentName() {
		//// Stage
		return trans.get("BoosterSet.BoosterSet");
	}
	
	// not strictly accurate, but this should provide an acceptable estimate for total vehicle size 
	@Override
	public Collection<Coordinate> getComponentBounds() {
		Collection<Coordinate> bounds = new ArrayList<Coordinate>(8);
		final double WAG_FACTOR = 1.1;
		double x_min = Double.MAX_VALUE;
		double x_max = Double.MIN_VALUE;
		double r_max = 0;
		
		Coordinate[] instanceLocations = this.getLocation();
		
		for (Coordinate currentInstanceLocation : instanceLocations) {
			if (x_min > (currentInstanceLocation.x)) {
				x_min = currentInstanceLocation.x;
			}
			if (x_max < (currentInstanceLocation.x + this.length)) {
				x_max = currentInstanceLocation.x + this.length;
			}
			if (r_max < (this.getRadialOffset() * WAG_FACTOR)) {
				r_max = this.getRadialOffset() * WAG_FACTOR;
			}
		}
		addBound(bounds, x_min, r_max);
		addBound(bounds, x_max, r_max);
		
		return bounds;
	}
	
	/**
	 * Check whether the given type can be added to this component.  A Stage allows
	 * only BodyComponents to be added.
	 *
	 * @param type The RocketComponent class type to add.
	 *
	 * @return Whether such a component can be added.
	 */
	@Override
	public boolean isCompatible(Class<? extends RocketComponent> type) {
		return BodyComponent.class.isAssignableFrom(type);
	}
	
	@Override
	public void cloneFlightConfiguration(String oldConfigId, String newConfigId) {
		separationConfigurations.cloneFlightConfiguration(oldConfigId, newConfigId);
	}
	
	@Override
	protected RocketComponent copyWithOriginalID() {
		BoosterSet copy = (BoosterSet) (super.copyWithOriginalID());
		return copy;
	}
	
	@Override
	public Coordinate[] getLocation() {
		if (null == this.parent) {
			throw new BugException(" Attempted to get absolute position Vector of a Stage without a parent. ");
		}
		
		Coordinate[] parentInstances = this.parent.getLocation();
		if (1 != parentInstances.length) {
			throw new BugException(" OpenRocket does not (yet) support external stages attached to external stages. " +
					"(assumed reason for getting multiple parent locations into an external stage.)");
		}
		
		Coordinate[] toReturn = this.shiftCoordinates(parentInstances);
		
		return toReturn;
	}
	
	@Override
	public boolean getOutside() {
		return !isCenterline();
	}
	
	/**
	 * Boosters are, by definition, not centerline. 
	 * 
	 * @return whether this Stage is along the center line of the Rocket. Always false.
	 */
	@Override
	public boolean isCenterline() {
		return false;
	}
	
	@Override
	public int getInstanceCount() {
		return this.count;
	}
	
	@Override
	public void setInstanceCount(final int _count) {
		mutex.verify();
		if (_count < 2) {
			// there must be at least one instance....   
			return;
		}
		
		this.count = _count;
		this.angularSeparation = Math.PI * 2 / this.count;
		fireComponentChangeEvent(ComponentChangeEvent.BOTH_CHANGE);
	}
	
	@Override
	public double getAngularOffset() {
		return this.angularPosition_rad;
	}
	
	@Override
	public void setAngularOffset(final double angle_rad) {
		this.angularPosition_rad = angle_rad;
		fireComponentChangeEvent(ComponentChangeEvent.BOTH_CHANGE);
	}
	
	@Override
	public double getRadialOffset() {
		return this.radialPosition_m;
	}
	
	@Override
	public void setRadialOffset(final double radius) {
		//		log.error("  set radial position for: " + this.getName() + " to: " + this.radialPosition_m + " ... in meters?");
		this.radialPosition_m = radius;
		fireComponentChangeEvent(ComponentChangeEvent.BOTH_CHANGE);
	}
	
	@Override
	public void setRelativePositionMethod(final Position _newPosition) {
		if (null == this.parent) {
			throw new NullPointerException(" a Stage requires a parent before any positioning! ");
		}
		
		super.setRelativePosition(_newPosition);
		
		fireComponentChangeEvent(ComponentChangeEvent.AERODYNAMIC_CHANGE);
	}
	
	@Override
	public double getPositionValue() {
		mutex.verify();
		
		return this.getAxialOffset();
	}
	
	/** 
	 * Stages may be positioned relative to other stages. In that case, this will set the stage number 
	 * against which this stage is positioned.
	 * 
	 * @return the stage number which this stage is positioned relative to
	 */
	@Override
	public int getRelativeToStage() {
		if (null == this.parent) {
			return -1;
		} else if (this.parent instanceof BoosterSet) {
			return this.parent.parent.getChildPosition(this.parent);
		} else if (this.isCenterline()) {
			if (0 < this.stageNumber) {
				return --this.stageNumber;
			}
		}
		
		return -1;
	}
	
	@Override
	public int getStageNumber() {
		return this.stageNumber;
	}
	
	@Override
	public double getAxialOffset() {
		double returnValue = Double.NaN;
		
		if ((this.isCenterline() && (Position.AFTER != this.relativePosition))) {
			// remember the implicit (this instanceof Stage)
			throw new BugException("found a Stage on centerline, but not positioned as AFTER.  Please fix this! " + this.getName() + "  is " + this.getRelativePosition().name());
		} else {
			returnValue = super.asPositionValue(this.relativePosition);
		}
		
		if (0.000001 > Math.abs(returnValue)) {
			returnValue = 0.0;
		}
		
		return returnValue;
	}
	
	@Override
	public void setAxialOffset(final double _pos) {
		this.updateBounds();
		super.setAxialOffset(this.relativePosition, _pos);
		fireComponentChangeEvent(ComponentChangeEvent.BOTH_CHANGE);
	}
	
	@Override
	public Coordinate[] shiftCoordinates(Coordinate[] c) {
		checkState();
		
		if (this.isCenterline()) {
			return c;
		}
		
		if (1 < c.length) {
			throw new BugException("implementation of 'shiftCoordinates' assumes the coordinate array has len == 1; this is not true, and may produce unexpected behavior! ");
		}
		
		double radius = this.radialPosition_m;
		double angle0 = this.angularPosition_rad;
		double angleIncr = this.angularSeparation;
		Coordinate center = this.position;
		Coordinate[] toReturn = new Coordinate[this.count];
		Coordinate thisOffset;
		double thisAngle = angle0;
		for (int instanceNumber = 0; instanceNumber < this.count; instanceNumber++) {
			thisOffset = center.add(0, radius * Math.cos(thisAngle), radius * Math.sin(thisAngle));
			
			toReturn[instanceNumber] = thisOffset.add(c[0]);
			thisAngle += angleIncr;
		}
		
		return toReturn;
	}
	
	@Override
	protected StringBuilder toDebugDetail() {
		StringBuilder buf = super.toDebugDetail();
		//		if (-1 == this.getRelativeToStage()) {
		//			System.err.println("      >>refStageName: " + null + "\n");
		//		} else {
		//			Stage refStage = (Stage) this.parent;
		//			System.err.println("      >>refStageName: " + refStage.getName() + "\n");
		//			System.err.println("      ..refCenterX: " + refStage.position.x + "\n");
		//			System.err.println("      ..refLength: " + refStage.getLength() + "\n");
		//		}
		return buf;
	}
	
	@Override
	public void toDebugTreeNode(final StringBuilder buffer, final String prefix) {
		
		String thisLabel = this.getName() + " (" + this.getStageNumber() + ")";
		
		buffer.append(String.format("%s    %-24s  %5.3f", prefix, thisLabel, this.getLength()));
		
		if (this.isCenterline()) {
			buffer.append(String.format("  %24s  %24s\n", this.getOffset(), this.getLocation()[0]));
		} else {
			buffer.append(String.format("  %4.1f  via: %s \n", this.getAxialOffset(), this.relativePosition.name()));
			Coordinate[] relCoords = this.shiftCoordinates(new Coordinate[] { Coordinate.ZERO });
			Coordinate[] absCoords = this.getLocation();
			
			for (int instanceNumber = 0; instanceNumber < this.count; instanceNumber++) {
				Coordinate instanceRelativePosition = relCoords[instanceNumber];
				Coordinate instanceAbsolutePosition = absCoords[instanceNumber];
				buffer.append(String.format("%s                 [instance %2d of %2d]  %32s  %32s\n", prefix, instanceNumber, count,
						instanceRelativePosition, instanceAbsolutePosition));
			}
		}
		
	}
	
	@Override
	public void updateBounds() {
		// currently only updates the length 
		this.length = 0;
		Iterator<RocketComponent> childIterator = this.getChildren().iterator();
		while (childIterator.hasNext()) {
			RocketComponent curChild = childIterator.next();
			this.length += curChild.getLength();
		}
		
	}
	
	@Override
	protected void update() {
		if (null == this.parent) {
			return;
		}
		
		this.updateBounds();
		// because if parent is instanceof Stage, that means 'this' is positioned externally 
		super.update();
		
		// updates the internal 'previousComponent' field.
		this.updateChildSequence();
		
		return;
	}
	
}
