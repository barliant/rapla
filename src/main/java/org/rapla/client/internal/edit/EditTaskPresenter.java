package org.rapla.client.internal.edit;

import com.google.web.bindery.event.shared.EventBus;
import org.rapla.RaplaResources;
import org.rapla.client.EditApplicationEventContext;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.ReservationEdit;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.event.ApplicationEvent;
import org.rapla.client.event.ApplicationEvent.ApplicationEventContext;
import org.rapla.client.event.TaskPresenter;
import org.rapla.client.internal.SaveUndo;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.entities.Category;
import org.rapla.entities.Entity;
import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.RaplaType;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.ModificationEvent;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.function.Consumer;
import org.rapla.inject.Extension;
import org.rapla.inject.ExtensionRepeatable;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
@ExtensionRepeatable({ @Extension(id = EditTaskPresenter.EDIT_EVENTS_ID, provides = TaskPresenter.class),
        @Extension(id = EditTaskPresenter.EDIT_RESOURCES_ID, provides = TaskPresenter.class),
        @Extension(id = EditTaskPresenter.CREATE_RESERVATION_FOR_DYNAMIC_TYPE, provides = TaskPresenter.class),
        @Extension(id = EditTaskPresenter.CREATE_RESERVATION_FROM_TEMPLATE, provides = TaskPresenter.class),
})
public class EditTaskPresenter implements TaskPresenter
{
    public static final String CREATE_RESERVATION_FROM_TEMPLATE = "reservationFromTemplate";
    private final DialogUiFactoryInterface dialogUiFactory;
    private final RaplaFacade raplaFacade;
    private final RaplaResources i18n;
    private final ClientFacade clientFacade;
    private final EventBus eventBus;
    private final CalendarSelectionModel model;

    public static final String CREATE_RESERVATION_FOR_DYNAMIC_TYPE = "createReservationFromDynamicType";
    final static public String EDIT_EVENTS_ID = "editEvents";
    final static public String EDIT_RESOURCES_ID = "editResources";
    private final Provider<ReservationEdit> reservationEditProvider;
    private final EditTaskView editTaskView;

    public interface EditTaskView
    {
//        interface Presenter<T>
//        {
//            void save(List<T> saveObjects);
//            void close();
//        }
        <T  extends Entity> RaplaWidget doSomething(Collection<T> toEdit,String title,Consumer<Collection<T>> save, Runnable close) throws RaplaException;
    }

    @Inject
    public EditTaskPresenter(ClientFacade clientFacade, EditTaskView editTaskView, DialogUiFactoryInterface dialogUiFactory,
            RaplaResources i18n, EventBus eventBus, CalendarSelectionModel model, Provider<ReservationEdit> reservationEditProvider)
    {
        this.editTaskView = editTaskView;
        this.dialogUiFactory = dialogUiFactory;
        this.i18n = i18n;
        this.clientFacade = clientFacade;
        this.eventBus = eventBus;
        this.model = model;
        this.reservationEditProvider = reservationEditProvider;
        this.raplaFacade = clientFacade.getRaplaFacade();
    }

    @Override
    public Promise<RaplaWidget> startActivity(ApplicationEvent applicationEvent)
    {
        final String taskId = applicationEvent.getApplicationEventId();
        String info = applicationEvent.getInfo();
        PopupContext popupContext = applicationEvent.getPopupContext();
        try
        {
            if (taskId.equals(EDIT_RESOURCES_ID) || taskId.equals(EDIT_EVENTS_ID))
            {
                final ApplicationEventContext context = applicationEvent.getContext();
                List<Entity> entities = new ArrayList<>();
                if (context != null && context instanceof EditApplicationEventContext)
                {
                    final EditApplicationEventContext editApplicationEventContext = (EditApplicationEventContext) context;
                    entities.addAll(editApplicationEventContext.getSelectedObjects());
                    final AppointmentBlock appointmentBlock = editApplicationEventContext.getAppointmentBlock();
                    if (appointmentBlock != null)
                    {
                        ReservationEdit<?> c = reservationEditProvider.get();
                        final Reservation reservation = appointmentBlock.getAppointment().getReservation();
                        c.editReservation(reservation, appointmentBlock, applicationEvent);
                        return new ResolvedPromise<RaplaWidget>(c);
                    }
                }
                else
                {
                    String[] ids = ((String) info).split(",");
                    Class<? extends Entity> clazz = taskId.equals(EDIT_RESOURCES_ID) ? Allocatable.class : Reservation.class;
                    for (String id : ids)
                    {
                        Entity<?> resolve;
                        try
                        {
                            resolve = raplaFacade.resolve(new ReferenceInfo(id, clazz));
                        }
                        catch (EntityNotFoundException e)
                        {
                            return new ResolvedPromise<RaplaWidget>(e);
                        }
                        entities.add(resolve);
                    }
                }
                String title = null;
                RaplaWidget<?> edit = createEditDialog(entities, title, popupContext, applicationEvent);
                return new ResolvedPromise<RaplaWidget>(edit);
            }
            else if (CREATE_RESERVATION_FOR_DYNAMIC_TYPE.equals(taskId))
            {
                final String dynamicTypeId = info;
                final Entity resolve = raplaFacade.resolve(new ReferenceInfo<Entity>(dynamicTypeId, DynamicType.class));
                final DynamicType type = (DynamicType) resolve;
                Classification newClassification = type.newClassification();
                final User user = clientFacade.getUser();
                Reservation r = raplaFacade.newReservation(newClassification, user);
                Appointment appointment = createAppointment();
                r.addAppointment(appointment);
                final List<Reservation> singletonList = Collections.singletonList(r);
                List<Reservation> list = RaplaComponent.addAllocatables(model, singletonList, user);
                String title = null;
                final RaplaWidget<?> editDialog = createEditDialog(list, title, popupContext, applicationEvent);
                return new ResolvedPromise<RaplaWidget>(editDialog);
            }
            else if (CREATE_RESERVATION_FROM_TEMPLATE.equals(taskId))
            {

                final String templateId = info;
                User user = clientFacade.getUser();
                Allocatable template = findTemplate(templateId);
                final Promise<Collection<Reservation>> templatePromise = raplaFacade.getTemplateReservations(template);
                Promise<RaplaWidget> widgetPromise = templatePromise.thenApply((reservations) ->
                {
                    if (reservations.size() == 0)
                    {
                        throw new EntityNotFoundException("Template " + template + " is empty. Please create events in template first.");
                    }
                    Boolean keepOrig = (Boolean) template.getClassification().getValue("fixedtimeandduration");
                    Collection<TimeInterval> markedIntervals = model.getMarkedIntervals();
                    boolean markedIntervalTimeEnabled = model.isMarkedIntervalTimeEnabled();
                    boolean keepTime = !markedIntervalTimeEnabled || (keepOrig == null || keepOrig);
                    Date beginn = RaplaComponent.getStartDate(model, raplaFacade, user);
                    Collection<Reservation> newReservations = raplaFacade.copy(reservations, beginn, keepTime, user);
                    if (markedIntervals.size() > 0 && reservations.size() == 1 && reservations.iterator().next().getAppointments().length == 1
                            && keepOrig == Boolean.FALSE)
                    {
                        Appointment app = newReservations.iterator().next().getAppointments()[0];
                        TimeInterval first = markedIntervals.iterator().next();
                        Date end = first.getEnd();
                        if (!markedIntervalTimeEnabled)
                        {
                            end = DateTools.toDateTime(end, app.getEnd());
                        }
                        if (!beginn.before(end))
                        {
                            end = new Date(app.getStart().getTime() + DateTools.MILLISECONDS_PER_HOUR);
                        }
                        app.move(app.getStart(), end);
                    }
                    List<Reservation> list = RaplaComponent.addAllocatables(model, newReservations, user);
                    String title = null;
                    return createEditDialog(list, title, popupContext, applicationEvent);
                });
                return widgetPromise;
            }
            else
            {
                return new ResolvedPromise<RaplaWidget>(new RaplaException("Unknow taskId" + taskId));
            }
        }
        catch (RaplaException e)
        {
            return new ResolvedPromise<RaplaWidget>(e);
        }
    }

    private Allocatable findTemplate(String templateId) throws RaplaException
    {
        final Collection<Allocatable> templates = raplaFacade.getTemplates();
        for (Allocatable allocatable : templates)
        {
            if (allocatable.getId().equals(templateId))
            {
                return allocatable;
            }
        }
        return null;
    }

    protected Appointment createAppointment() throws RaplaException
    {

        Date startDate = RaplaComponent.getStartDate(model, raplaFacade, clientFacade.getUser());
        Date endDate = RaplaComponent.calcEndDate(model, startDate);
        Appointment appointment = raplaFacade.newAppointment(startDate, endDate);
        return appointment;
    }

    //	enhancement of the method to deal with arrays
    private String guessTitle(Collection obj)
    {
        Class<? extends Entity> raplaType = getRaplaType(obj);
        String title = "";
        if (raplaType != null)
        {
            String localname = RaplaType.getLocalName(raplaType);
            title = i18n.getString(localname);
        }

        return title;
    }

    //	method for determining the consistent RaplaType from different objects
    protected Class<? extends Entity> getRaplaType(Collection obj)
    {
        Set<Class<? extends Entity>> types = new HashSet<Class<? extends Entity>>();

        //		iterate all committed objects and store RaplayType of the objects in a Set
        //		identic typs aren't stored double because of Set
        for (Object o : obj)
        {
            if (o instanceof Entity)
            {
                final Class<? extends Entity> type = ((Entity) o).getTypeClass();
                types.add(type);
            }
        }

        //		check if there is a explicit type, then return this type; otherwise return null
        if (types.size() == 1)
            return types.iterator().next();
        else
            return null;
    }

    private <T extends Entity> RaplaWidget createEditDialog(List<T> list, String title, PopupContext popupContext, ApplicationEvent applicationEvent)
            throws RaplaException
    {
        if (list.size() == 0)
        {
            throw new RaplaException("Empty list not allowed. You must have at least one entity to edit.");
        }
        if (title == null)
        {
            title = guessTitle(list);

        }
        //		checks if all entities are from the same type; otherwise return
        if (getRaplaType(list) == null)
        {
            return null;
        }

        if (list.size() == 1)
        {
            Entity<?> testObj = (Entity<?>) list.get(0);
            if (testObj instanceof Reservation)
            {
                ReservationEdit<?> c = reservationEditProvider.get();
                c.editReservation((Reservation) testObj, null, applicationEvent);
                return c;
            }
        }
        //		gets for all objects in array a modifiable version and add it to a set to avoid duplication
        Collection<T> nonEditableObjects = new ArrayList<>(list);
        Collection<T> toEdit = new ArrayList<>();
        for (Iterator<T> iterator = nonEditableObjects.iterator(); iterator.hasNext(); )
        {
            T t = iterator.next();
            if (!t.isReadOnly())
            {
                iterator.remove();
                toEdit.add(t);
            }
        }
        toEdit.addAll(raplaFacade.edit(nonEditableObjects));
        List<T> originals = new ArrayList<T>();
        Map<T, T> persistant = raplaFacade.getPersistant(nonEditableObjects);
        for (T entity : toEdit)
        {

            @SuppressWarnings("unchecked") Entity<T> mementable = persistant.get(entity);
            if (mementable != null)
            {
                if (originals == null)
                {
                    throw new RaplaException("You cannot edit persistant and new entities in one operation");
                }
                originals.add(mementable.clone());
            }
            else
            {
                if (originals != null && !originals.isEmpty())
                {
                    throw new RaplaException("You cannot edit persistant and new entities in one operation");
                }
                originals = null;
            }
        }
        final List<T> origs = originals;
        if (toEdit.size() > 0)
        {
            org.rapla.function.Consumer<Collection<T>> saveCmd =  (saveObjects) ->
            {
                Collection<T> entities = new ArrayList<T>();
                entities.addAll(saveObjects);
                boolean canUndo = true;
                for (T obj : saveObjects)
                {
                    if (obj instanceof Preferences || obj instanceof DynamicType || obj instanceof Category)
                    {
                        canUndo = false;
                    }
                }
                if (canUndo)
                {
                    @SuppressWarnings({ "unchecked", "rawtypes" })
                    SaveUndo<T> saveCommand = new SaveUndo(raplaFacade, i18n, entities, origs);
                    CommandHistory commandHistory = clientFacade.getCommandHistory();
                    Promise promise = commandHistory.storeAndExecute(saveCommand);
                    promise.thenRun(() ->
                    {
                        //                                    getPrivateEditDialog().removeEditDialog(EditDialog.this);
                        //                                    dlg.close();
                        //       FIXME callback;
                    });
                }
                else
                {
                    raplaFacade.storeObjects(saveObjects.toArray(new Entity[] {}));
                }
                //getPrivateEditDialog().removeEditDialog(EditDialog.this);
                close(applicationEvent);
            };

            Runnable closeCmd = () -> close(applicationEvent);
            return editTaskView.doSomething(toEdit,title,saveCmd,closeCmd);
        }
        return null;
    }

    public void close(ApplicationEvent applicationEvent)
    {
        applicationEvent.setStop(true);
        eventBus.fireEvent(applicationEvent);
    }

    @Override
    public void updateView(ModificationEvent event)
    {
        // FIXME
    }



    //    public void dataChanged(ModificationEvent evt) throws RaplaException
    //    {
    //        super.dataChanged(evt);
    //        if (bSaving || dlg == null || !dlg.isVisible() || ui == null)
    //            return;
    //        if (shouldCancelOnModification(evt))
    //        {
    //            getPrivateEditDialog().removeEditDialog(this);
    //        }
    //    }
    //
    //    @Override
    //    protected void cleanupAfterClose()
    //    {
    //        getPrivateEditDialog().removeEditDialog(EditDialog.this);
    //    }

    class SaveAction<T> implements Runnable
    {
        private static final long serialVersionUID = 1L;

        public SaveAction()
        {
        }

        public void run()
        {

        }
    }



}
