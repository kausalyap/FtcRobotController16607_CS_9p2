
package TrcCommonLib.trclib;

/**
 * This class implements a Cascade PID Controller. A Cascade PID controller consists of two PID controllers in cascade.
 * The output of the primary PID controller feeds into the input of the secondary PID controller. If the motor is not
 * linear, it may be very difficult to get good performance out of a single PID controller. In Cascade PID control,
 * the distance set-point, for example, will produce a speed control as the primary output and feeds into the
 * secondary PID controller as input that will try to compensate for the non-linearity of the motor or even battery
 * level changes. The TrcCascadePidController class extends a regular PID control as its primary PID controller and
 * creates a second PID controller as its secondary controller.
 */
public class TrcCascadePidController extends TrcPidController
{
    public TrcPidController secondaryCtrl;

    /**
     * Constructor: Create an instance of the object.
     *
     * @param instanceName specifies the instance name.
     * @param primaryPidCoefficients specifies the PID coefficients of the primary PID controller.
     * @param primaryTolerance specifies the target tolerance of the primary PID controller.
     * @param primarySettlingTime specifies the target settling time of the primary PID controller.
     * @param secondaryPidCoefficients specifies the PID coefficients of the secondary PID controller.
     * @param secondaryTolerance specifies the target tolerance of the secondary PID controller.
     * @param secondarySettlingTime specifies the target settling time of the secondary PID controller.
     * @param primaryInput specifies the supplier of the primary PID input.
     * @param secondaryInput specifies the supplier of the secondary PID input.
     */
    public TrcCascadePidController(
        String instanceName,
        PidCoefficients primaryPidCoefficients, double primaryTolerance, double primarySettlingTime,
        PidCoefficients secondaryPidCoefficients, double secondaryTolerance, double secondarySettlingTime,
        PidInput primaryInput, PidInput secondaryInput)
    {
        super(instanceName + ".primary", primaryPidCoefficients, primaryTolerance, primarySettlingTime, primaryInput);
        secondaryCtrl = new TrcPidController(
            instanceName + ".secondary", secondaryPidCoefficients, secondaryTolerance, secondarySettlingTime,
            secondaryInput);
    }   //TrcCascadePidController

    /**
     * Constructor: Create an instance of the object.
     *
     * @param instanceName specifies the instance name.
     * @param primaryPidCoefficients specifies the PID coefficients of the primary PID controller.
     * @param primaryTolerance specifies the target tolerance of the primary PID controller.
     * @param secondaryPidCoefficients specifies the PID coefficients of the secondary PID controller.
     * @param secondaryTolerance specifies the target tolerance of the secondary PID controller.
     * @param primaryInput specifies the supplier of the primary PID input.
     * @param secondaryInput specifies the supplier of the secondary PID input.
     */
    public TrcCascadePidController(
        String instanceName,
        PidCoefficients primaryPidCoefficients, double primaryTolerance,
        PidCoefficients secondaryPidCoefficients, double secondaryTolerance,
        PidInput primaryInput, PidInput secondaryInput)
    {
        this(instanceName,
             primaryPidCoefficients, primaryTolerance, DEF_SETTLING_TIME,
             secondaryPidCoefficients, secondaryTolerance, DEF_SETTLING_TIME,
             primaryInput, secondaryInput);
    }   //TrcCascadePidController

    /**
     * This method is called to reset the Cascade PID controller. It resets both the primary and secondary PID
     * controller.
     */
    @Override
    public synchronized void reset()
    {
        secondaryCtrl.reset();
        super.reset();
    }   //reset

    /**
     * This method calculates the Cascade PID control output by calling the primary PID controller, feeding its
     * output to the secondary PID controller and finally returning the output of the secondary PID controller.
     * @return output of the Cascade PID controller.
     */
    @Override
    public synchronized double getOutput()
    {
        secondaryCtrl.setTarget(super.getOutput());
        return secondaryCtrl.getOutput();
    }   //getOutput

}   //class TrcCascadePidController
