package ev3WallFollower;


/**
 * Interface to declare ultrasonic reading methods.
 */
public interface UltrasonicController {
	
	public void processUSData(int leftUltraSonicDistance , int rightUltraSonicDistance);
	
	public int readUSDistance();
	
}

