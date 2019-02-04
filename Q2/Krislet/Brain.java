//
//	File:			Brain.java
//	Author:		Krzysztof Langner
//	Date:			1997/04/28
//
//    Modified by:	Paul Marlow

//    Modified by:      Edgar Acosta
//    Date:             March 4, 2008

import java.lang.Math;
import java.util.regex.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

class Brain extends Thread implements SensorInput
{
    //---------------------------------------------------------------------------
    // This constructor:
    // - stores connection to krislet
    // - starts thread for this object
    public Brain(SendCommand krislet, 
		 String team, 
		 char side, 
		 int number, 
		 String playMode)
    {
		m_timeOver = false;
		m_krislet = krislet;
		m_memory = new Memory();
		//m_team = team;
		m_side = side;
		// m_number = number;
		m_playMode = playMode;
		start();
    }

    //---------------------------------------------------------------------------
    // This is main brain function used to make decision
    // In each cycle we decide which command to issue based on
    // current situation. the rules are:
    //
    //	1. If you don't know where is ball then turn right and wait for new info
    //
    //	2. If ball is too far to kick it then
    //		2.1. If we are directed towards the ball then go to the ball
    //		2.2. else turn to the ball
    //
    //	3. If we dont know where is opponent goal then turn wait 
    //				and wait for new info
    //
    //	4. Kick ball
    //
    //	To ensure that we don't send commands to often after each cycle
    //	we waits one simulator steps. (This of course should be done better)

    // ***************  Improvements ******************
    // Allways know where the goal is.
    // Move to a place on my side on a kick_off
    // ************************************************

    public void run()
    {
		System.out.println("Thread Begin");
		ObjectInfo ballObject;
		ObjectInfo goalObject;

		int numOfDashed = 0; // State Variable to count the number of dashed by an agent

		// first put it somewhere on my side
		if (Pattern.matches("^before_kick_off.*",m_playMode))
	    m_krislet.move( -Math.random()*52.5 , 34 - Math.random()*68.0 );

		while( !m_timeOver )
		{
			String cartesEnv = "";	// For Cartesian product of Environment: E
			String envCartesState = ""; // // For Cartesian product of Environment and State: E x Q

			// Get Environment Info
			ballObject = m_memory.getObject("ball");

			if( m_side == 'l' )
			{
				goalObject = m_memory.getObject("goal r");
			}
			else
			{
				goalObject = m_memory.getObject("goal l");
			}

			// Compute Cartesian product of the current Environment from ballObject, goalObject and agent's previous state
			if( ballObject == null )
			{
				cartesEnv = "BallNull"; // case:0
				envCartesState = cartesEnv;
			}
			else if( ballObject.m_distance > 1.0 )
		    {
				if ( ballObject.m_direction != 0 )
				{
					cartesEnv = "Ball X BallDistanceGreaterOne X BallDirectionIncorrect"; // case:1
					envCartesState = cartesEnv;
				}
				else
				{
					cartesEnv = "Ball X BallDistanceGreaterOne X BallDirectionCorrect"; 
					
					if (numOfDashed < 10)
					{	
						System.out.println("Agent feels good.");
						envCartesState = cartesEnv + " X numOfDashedLessThanTen"; // case:3
					}
					else
					{ 
						System.out.println("Agent is tired..");
						envCartesState = cartesEnv + " X numOfDashedMoreThanTen"; // case:2
					}
				}
		    }
			else
		    {
				if( goalObject == null )
			    { 
					cartesEnv = "Ball X BallDistanceLessOne X GoalNull"; // case:0
					envCartesState = cartesEnv;
			    }
				else
				{
					if( m_side == 'l' )
					{
						cartesEnv = "Ball X BallDistanceLessOne X GoalRight";
					}
					else
					{
						cartesEnv = "Ball X BallDistanceLessOne X GoalLeft";
					}

					if (numOfDashed < 10)
					{
						System.out.println("Agent feels good.");
						envCartesState = cartesEnv + " X numOfDashedLessThanTen"; // case:5
					}
					else
					{
						System.out.println("Agent is tired..");
						envCartesState = cartesEnv + " X numOfDashedMoreThanTen"; // case:4
					}
				}
		    }
			// Map the agent action from AgentFunction.txt based on the current envCartesState, E x Q
			int actionNumber = -1;
			BufferedReader reader;

			// Read the action number from AgentFunction.txt based on tthe current envCartesState, E x Q
			try 
			{
				reader = new BufferedReader(new FileReader("AgentFunction.txt"));
				String line =  reader.readLine();

				while (line != null) 
				{
					line = reader.readLine(); 
					
					if (line == null)
					{
						break;
					}

					char lastChar = line.charAt(line.length() - 1);
					
					if (line.equals(envCartesState + '_' + lastChar))
					{	
						actionNumber = Character.getNumericValue(lastChar); 
						break;
					}
		
				}

				reader.close(); // Close AgentFunction.txt
			} 
			catch (IOException e) 
			{
				e.printStackTrace();
			}
			// Execute agent's action based on the action number
			switch(actionNumber)
			{
				case 0: // Currently agent doesn't know where is ball, then Turn and wait for new info to find the ball
				{
					m_krislet.turn(40);
					m_memory.waitForNewInfo();
					break;
				}
				case 1: // Currently incorrect direction so Turn to the correct direction to ball
				{
					m_krislet.turn(ballObject.m_direction);  
					break;
				}
				case 2:  // Currently correct direction and and numOfDashed > 10, so Run with 30 power until refreshed
				{
					m_krislet.dash(30);

					numOfDashed++; // Update Agent's state: Add the number of dashed

					if (numOfDashed > 12) 
					{
						numOfDashed = 0;
						System.out.println("Agent is refreshed!");
					}

					break;
				}
				case 3: // Currently correct direction and and numOfDashed < 10, so Run with 100 power
				{
					m_krislet.dash(100);

					numOfDashed++; // Update Agent's state: Add the number of dashed

					break;
				}
				case 4:  // Currently right distance, correct direction, and numOfDashed > 10,  so Kick the ball with 30 power
				{
					m_krislet.kick(30, goalObject.m_direction);
					break;
				}
				case 5:  // Currently right distance, correct direction, and numOfDashed < 10,  so Kick the ball with 50 power
				{
					m_krislet.kick(50, goalObject.m_direction);
					break;
				}
				default: break;
			}
			// sleep one step to ensure that we will not send
			// two commands in one cycle.
			try
			{
				Thread.sleep(2*SoccerParams.simulator_step);
			}
			catch(Exception e){}
		}
		System.out.println("Thread End");
		m_krislet.bye();
    }

    //===========================================================================
    // Here are suporting functions for implement logic
    //===========================================================================
    // Implementation of SensorInput Interface
    //---------------------------------------------------------------------------
    // This function sends see information
    public void see(VisualInfo info)
    {
		m_memory.store(info);
    }

    //---------------------------------------------------------------------------
    // This function receives hear information from player
    public void hear(int time, int direction, String message)
    {
    }

    //---------------------------------------------------------------------------
    // This function receives hear information from referee
    public void hear(int time, String message)
    {						 
		if(message.compareTo("time_over") == 0)
			m_timeOver = true;

    }

    //===========================================================================
    // Private members
    private SendCommand	                m_krislet;			// robot which is controled by this brain
    private Memory			m_memory;				// place where all information is stored
    private char			m_side;
    volatile private boolean		m_timeOver;
    private String                      m_playMode;
    
}
