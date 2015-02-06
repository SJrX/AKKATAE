
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

public class MasterActor extends UntypedActor {

	LoggingAdapter log = Logging.getLogger(getContext().system(), this);
	@Override
	public void onReceive(Object message) throws Exception {
		if(message instanceof String)
		{
			log.info("Recieved message: {} ", message);
			getSender().tell("Guess what I saw:" + message, getSelf());
		} else
		{
			unhandled(message);
		}
		
	}

}
