package ev3Navigator;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import ev3ObjectDetector.ObjectDetector;
import ev3ObjectDetector.ObstacleAvoider;
import ev3Objects.CaptureTheFlagGameObject;
import ev3Objects.Coordinate;
import ev3Objects.Motors;
import ev3Objects.ObstacleOnCoordinateException;
import ev3Odometer.Odometer;
import lejos.hardware.Sound;
import lejos.hardware.motor.EV3LargeRegulatedMotor;

/**
 * Creates an object used to Navigate through the environment
 */
public class Navigator extends Thread{

	private EV3LargeRegulatedMotor leftMotor;
	private EV3LargeRegulatedMotor rightMotor;
	private ObjectDetector objectDetector;
	private ObstacleAvoider obstacleAvoider;
	private Odometer odometer;
	private CaptureTheFlagGameObject captureTheFlagGameObject;

	private double wheelRadius;
	private double axleLength;
	private boolean isAvoiding = false;
	private boolean hasAvoided = false;

	private final double locationError = 1;
	private final double navigatingAngleError = 1;
	private static final double  tileLength = 30.48;

	private final int FORWARD_SPEED = 200;
	private final int ROTATE_SPEED = 100;
	private final int SMALL_CORRECTION_SPEED =20;
	private final int SMALL_ROTATION_SPEED = 25;
	public NavigatorMotorCommands navigatorMotorCommands;


	public static int coordinateCount = 0;


	/**
	 * Store useful objects to be used by the Navigator
	 * @param pOdometer The Odometer to be used by the navigator
	 * @param pObjectDetector The ObjectDetector to be used by the navigator
	 * @param pObstacleAvoider The Obstacle avoider to be used by the navigator
	 * @param pMotors The Motors to be used by the navigator
	 */
	public Navigator(Odometer pOdometer, ObjectDetector pObjectDetector, ObstacleAvoider pObstacleAvoider, Motors pMotors)
	{
		odometer 					= pOdometer;
		objectDetector 				= pObjectDetector;
		obstacleAvoider				= pObstacleAvoider;
		leftMotor 					= pMotors.getLeftMotor();
		rightMotor 					= pMotors.getRightMotor();
		wheelRadius 				= pMotors.getWheelRadius();
		axleLength 					= pMotors.getAxleLength();

		for (EV3LargeRegulatedMotor motor : new EV3LargeRegulatedMotor[] { leftMotor, rightMotor }) {
			motor.stop();
			motor.setAcceleration(2000);

		}

		navigatorMotorCommands = new NavigatorMotorCommands(leftMotor, rightMotor);

	}

	@Override
	public void run()
	{
		Sound.beepSequenceUp();

		odometer.setX(captureTheFlagGameObject.getStartingCoordinate().getX());
		odometer.setY(captureTheFlagGameObject.getStartingCoordinate().getY());

		for(Coordinate coordinate : captureTheFlagGameObject.getPreSearchLocalizationCoordinates())
		{
			try{
				travelTo(coordinate.getX(), coordinate.getY());
			}
			catch (ObstacleOnCoordinateException e){
				continue;
			}

			break;
		}


		navigatorMotorCommands.stopMotors();
	}
	/**
	 * Travel to a coordinate while avoiding objects
	 * @param pX The x coordinate to travel to
	 * @param pY The y coordinate to travel to
	 */
	public void travelTo(double pX, double pY)
	{

		//While the robot is not at the objective coordinates, keep moving towards it 
		while(Math.abs(pX- odometer.getX()) > locationError || Math.abs(pY - odometer.getY()) > locationError)
		{
			if(objectDetector.detectedObject())
			{
				isAvoiding = true;
				determineIfObjectIsOnDestinationCoordinate(pX, pY);
				obstacleAvoider.avoidObstacle(pX, pY);
				isAvoiding = false;
				hasAvoided = true;
			}
			moveToCoordinates(pX, pY);

		}

		navigatorMotorCommands.stopMotors();

	}
	/**
	 * Turns to absolute value theta
	 * @param pTheta The angle to turn to
	 * @param useSmallRotationSpeed Whether to turn slowly or not
	 */
	public void turnTo(double pTheta, boolean useSmallRotationSpeed)
	{
		
		navigatorMotorCommands.stopMotors();
		
		pTheta = pTheta % Math.toRadians(360);

		double deltaTheta = pTheta - odometer.getTheta();

		double rotationAngle = 0;

		if( Math.abs(deltaTheta) <= Math.PI)
			rotationAngle = deltaTheta;

		if(deltaTheta < -Math.PI)
			rotationAngle = deltaTheta + 2*Math.PI;

		if(deltaTheta > Math.PI)
			rotationAngle = deltaTheta - 2*Math.PI;

		//Basic proportional control on turning speed when
		//making a small angle correction
		if(Math.abs(deltaTheta)<= Math.toRadians(10) || useSmallRotationSpeed)
		{
			leftMotor.setSpeed(SMALL_ROTATION_SPEED);
			rightMotor.setSpeed(SMALL_ROTATION_SPEED);
		}
		else
		{
			leftMotor.setSpeed(ROTATE_SPEED);
			rightMotor.setSpeed(ROTATE_SPEED);
		}

		leftMotor.rotate(-NavigatorUtility.convertAngle(wheelRadius, axleLength, rotationAngle * 180/Math.PI), true);
		rightMotor.rotate(NavigatorUtility.convertAngle(wheelRadius, axleLength, rotationAngle * 180/Math.PI), false);
	}


	/**
	 * Navigates to given coordinates
	 * @param pX The x coordinate to travel to
	 * @param pY The y coordinate to travel to
	 */
	private void moveToCoordinates(double pX, double pY)
	{
		double currentX = odometer.getX();
		double currentY = odometer.getY();

		double newAngle = NavigatorUtility.calculateNewAngle(pX - currentX, pY - currentY);


		if(Math.abs(  Math.toDegrees(NavigatorUtility.calculateShortestTurningAngle(newAngle, odometer.getTheta())))  > navigatingAngleError)
			turnTo(newAngle, false);
		else
		{
			//Basic proportional control, when the robot gets close to 
			//required coordinates, slow down 
			if(Math.abs(pX - currentX) <= 3 && Math.abs(pY - currentY ) <= 3)
			{
				leftMotor.setSpeed(SMALL_CORRECTION_SPEED);
				rightMotor.setSpeed(SMALL_CORRECTION_SPEED);
			}
			else
			{
				leftMotor.setSpeed(FORWARD_SPEED);
				rightMotor.setSpeed(FORWARD_SPEED);
			}
			leftMotor.forward();
			rightMotor.forward();
		}
	}


	private void determineIfObjectIsOnDestinationCoordinate(double pX, double pY){
		double objectX = odometer.getX() + Math.cos(odometer.getTheta()) * objectDetector.getObjectDistance();
		double objectY = odometer.getY() + Math.sin(odometer.getTheta()) * objectDetector.getObjectDistance();
		if(Math.abs(objectX-pX)<6 &&Math.abs(objectY-pY)<6)
			throw new ObstacleOnCoordinateException();

	}


	private void searchForFlag(Coordinate startPoint, Coordinate endPoint)
	{
		Queue<Coordinate> searchCoordinateQueue = new LinkedList<Coordinate>();

		if(Math.abs(endPoint.getY() - startPoint.getY())> Math.abs(endPoint.getY() - startPoint.getY())){

			double deltaX = endPoint.getX() - startPoint.getX();

			int coordinateCount =1;
			boolean useStartPoint = true;
			for(int count = 1; count < 9; count++){

				double xMultiplier = count;
				Coordinate coordinate;
				if(count % 2 ==0)
					xMultiplier = count -1;

				if(useStartPoint)
				{
					coordinate = new Coordinate(startPoint.getX() +deltaX * (xMultiplier/8), startPoint.getY());
				}
				else
				{
					coordinate = new Coordinate(startPoint.getX() +deltaX * (xMultiplier/8), endPoint.getY());
				}

				coordinateCount++;

				if(coordinateCount > 1)
				{
					coordinateCount = 0;
					useStartPoint =! useStartPoint;
				}
				searchCoordinateQueue.add(coordinate);
			}

		}
		else
		{

			double deltaY = endPoint.getY() - startPoint.getY();

			int coordinateCount =1;
			boolean useStartPoint = true;
			for(int count = 1; count < 9; count++){

				double yMultiplier = count;
				Coordinate coordinate;
				if(count % 2 ==0)
					yMultiplier = count - 1;

				if(useStartPoint)
				{
					coordinate = new Coordinate(startPoint.getX(), startPoint.getY() + deltaY * (yMultiplier/8));
				}
				else
				{
					coordinate = new Coordinate(startPoint.getX() , endPoint.getY() + deltaY * (yMultiplier/8));
				}

				coordinateCount++;

				if(coordinateCount > 1)
				{
					coordinateCount = 0;
					useStartPoint =! useStartPoint;
				}
				searchCoordinateQueue.add(coordinate);
			}
		}

		for(Coordinate coordinate : searchCoordinateQueue)
			while(Math.abs(coordinate.getX()- odometer.getX()) > locationError || Math.abs(coordinate.getY() - odometer.getY()) > locationError)
			{
				if(objectDetector.detectedObject())
				{
					Sound.beep();
					investigateObject();
				}
				moveToCoordinates(coordinate.getX(), coordinate.getY());
			}
	}

	/**
	 * Determine what the object is when in range
	 */
	private void investigateObject()
	{
		while(objectDetector.getObjectDistance() >=6 )
		{
			if(objectDetector.getObjectDistance()> objectDetector.getDefaultObstacleDistance())
				break;
			navigatorMotorCommands.driveStraight(30);
		}

		objectDetector.processObject();
	}

	/**
	 * Sets the game object for the capture the flag game 
	 * @param pCaptureTheFlagGameObject
	 */

	public void setGameObject(CaptureTheFlagGameObject pCaptureTheFlagGameObject)
	{
		captureTheFlagGameObject = pCaptureTheFlagGameObject;
	}

	/**
	 * Sets whether or not the EV3 has recently avoided an object
	 * @param update The boolean representing whether or not the EV3 has recently avoided an object
	 */
	public void setHasAvoided(boolean update)
	{
		this.hasAvoided = update;
	}
}

