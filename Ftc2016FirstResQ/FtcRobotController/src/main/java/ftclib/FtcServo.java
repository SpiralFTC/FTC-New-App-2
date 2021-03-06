/*
 * Titan Robotics Framework Library
 * Copyright (c) 2015 Titan Robotics Club (http://www.titanrobotics.net)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package ftclib;

import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.hardware.ServoController;

import trclib.TrcEvent;
import trclib.TrcRobot;
import trclib.TrcServo;
import trclib.TrcDbgTrace;
import trclib.TrcStateMachine;
import trclib.TrcTaskMgr;
import trclib.TrcTimer;

/**
 * This class implements a platform dependent servo extending TrcServo.
 * It provides implementation of the abstract methods in TrcServo.
 */
public class FtcServo extends TrcServo implements TrcTaskMgr.Task
{
    private static final String moduleName = "FtcServo";
    private static final boolean debugEnabled = false;
    private TrcDbgTrace dbgTrace = null;

    private enum State
    {
        SET_POSITION,
        DISABLE_CONTROLLER,
        DONE
    }   //enum State

    private static final double CONTROLLER_ONOFF_DELAY = 0.1;

    private String instanceName;
    private Servo servo;
    private ServoController controller;
    private TrcTimer timer;
    private TrcEvent event;
    private TrcStateMachine sm;
    private double servoPos = 0.0;
    private double servoOnTime = 0.0;

    /**
     * Constructor: Creates an instance of the object.
     *
     * @param hardwareMap specifies the global hardware map.
     * @param instanceName specifies the instance name.
     */
    public FtcServo(HardwareMap hardwareMap, String instanceName)
    {
        super(instanceName);

        if (debugEnabled)
        {
            dbgTrace = new TrcDbgTrace(
                    moduleName + "." + instanceName,
                    false,
                    TrcDbgTrace.TraceLevel.API,
                    TrcDbgTrace.MsgLevel.INFO);
        }

        this.instanceName = instanceName;
        servo = hardwareMap.servo.get(instanceName);
        controller = servo.getController();
        timer = new TrcTimer(instanceName);
        event = new TrcEvent(instanceName);
        sm = new TrcStateMachine(instanceName);
    }   //FtcServo

    /**
     * Constructor: Creates an instance of the object.
     *
     * @param instanceName specifies the instance name.
     */
    public FtcServo(String instanceName)
    {
        this(FtcOpMode.getInstance().hardwareMap, instanceName);
    }   //FtcServo

    /**
     * This method returns the servo controller object that this servo is plugged into.
     *
     * @return servo controller object.
     */
    public ServoController getController()
    {
        final String funcName = "getController";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API, controller.toString());
        }

        return controller;
    }   //getController

    /**
     * This method cancels the timer and stops the state machine if it is running.
     */
    public void cancel()
    {
        final String funcName = "cancel";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }

        if (sm.isEnabled())
        {
            timer.cancel();
            sm.stop();
            setTaskEnabled(false);
        }
    }

    /**
     * This method enables/disables the periodic task that runs the state machine.
     *
     * @param enabled specifies true to enable the state machine task, false otherwise.
     */
    private void setTaskEnabled(boolean enabled)
    {
        if (enabled)
        {
            TrcTaskMgr.getInstance().registerTask(
                    instanceName, this, TrcTaskMgr.TaskType.POSTCONTINUOUS_TASK);
        }
        else
        {
            TrcTaskMgr.getInstance().unregisterTask(this, TrcTaskMgr.TaskType.POSTCONTINUOUS_TASK);
        }
    }

    /**
     * This method sets the servo position but will cut power to the servo when done.
     * Since servo motors can't really take a lot of loads, it would stress out and
     * may burn out the servo if it is held against a heavy load for extended period
     * of time. This method allows us to set the position and only hold it long enough
     * for it to reach target position and then we will cut the servo controller
     * power off. Note that by doing so, all servos on the same controller will go
     * limp.
     *
     * @param pos specifies the target position.
     * @param onTime specifies the time in seconds to wait before disabling servo
     *               controller.
     */
    public void setPositionWithOnTime(double pos, double onTime)
    {
        cancel();
        servoPos = pos;
        servoOnTime = onTime;
        sm.start(State.SET_POSITION);
        setTaskEnabled(true);
    }   //setPositionWithOnTime

    /**
     * The method eanbles/disables the servo controller. If the servo controller is disabled,
     * all servos on the controller will go limp. This is useful for preventing the servos from
     * burning up if it is held against a heavy load.
     *
     * @param on specifies true to enable the servo controller, false otherwise.
     */
    public void setControllerOn(boolean on)
    {
        final String funcName = "setControllerOn";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API,
                                "on=%s", Boolean.toString(on));
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }

        if (on)
        {
            controller.pwmEnable();
        }
        else
        {
            controller.pwmDisable();
        }
    }   //setControllerOn

    //
    // Implements TrcServo abstract methods.
    //

    /**
     * This methods inverts the servo motor direction.
     *
     * @param inverted specifies true if the servo direction is inverted, false otherwise.
     */
    @Override
    public void setInverted(boolean inverted)
    {
        final String funcName = "setInverted";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API,
                                "inverted=%s", Boolean.toString(inverted));
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }

        servo.setDirection(inverted? Servo.Direction.REVERSE: Servo.Direction.FORWARD);
    }   //setInverted

    /**
     * This method returns true if the servo direction is inverted.
     *
     * @return true if the servo direction is inverted, false otherwise.
     */
    @Override
    public boolean getInverted()
    {
        final String funcName = "getInverted";
        boolean isInverted = servo.getDirection() == Servo.Direction.REVERSE;;

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API, "=%s", isInverted);
        }

        return isInverted;
    }   //getInverted

    /**
     * This method sets the servo motor position.
     *
     * @param position specifies the physical position of the servo motor.
     *                 This value may be in degrees if setPhysicalRange
     *                 is called with the degree range.
     */
    @Override
    public void setPosition(double position)
    {
        final String funcName = "setPosition";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API, "position=%f", position);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }

        servo.setPosition(toLogicalPosition(position));
    }   //setPosition

    /**
     * This method returns the physical position value of the servo motor.
     *
     * @return physical position of the servo, could be in degrees if
     *         setPhysicalRangis called to set the range in degrees.
     */
    @Override
    public double getPosition()
    {
        final String funcName = "getPosition";
        double position = toPhysicalPosition(servo.getPosition());

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API, "=%f", position);
        }

        return position;
    }   //getPosition

    //
    // Implements TrcTaskMgr.Task
    //

    @Override
    public void startTask(TrcRobot.RunMode runMode)
    {
    }   //startTask

    @Override
    public void stopTask(TrcRobot.RunMode runMode)
    {
    }   //stopTask

    @Override
    public void prePeriodicTask(TrcRobot.RunMode runMode)
    {
    }   //prePeriodicTask

    @Override
    public void postPeriodicTask(TrcRobot.RunMode runMode)
    {
    }   //postPeriodicTask

    @Override
    public void preContinuousTask(TrcRobot.RunMode runMode)
    {
    }   //preContinuousTask

    /**
     * This method is called periodically to run a state machine that will enable
     * the servo controller, set the servo position, wait for the specified hold
     * time, and finally disable the servo controller.
     *
     * @param runMode specifies the competition mode that is running.
     */
    @Override
    public void postContinuousTask(TrcRobot.RunMode runMode)
    {
        if (sm.isReady())
        {
            State state = (State)sm.getState();
            switch (state)
            {
                case SET_POSITION:
                    servo.setPosition(servoPos);
                    timer.set(servoOnTime, event);
                    sm.addEvent(event);
                    sm.waitForEvents(State.DISABLE_CONTROLLER);
                    break;

                case DISABLE_CONTROLLER:
                    controller.pwmDisable();
                    timer.set(CONTROLLER_ONOFF_DELAY, event);
                    sm.addEvent(event);
                    sm.waitForEvents(State.DONE);
                    break;

                case DONE:
                default:
                    sm.stop();
                    setTaskEnabled(false);
            }
        }
    }   //postContinuousTask

}   //class FtcServo
