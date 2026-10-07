package edu.vitap.tracking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
interface TrackedParcelRepository extends JpaRepository<TrackedParcel,String>{}
interface TrackingEntryRepository extends JpaRepository<TrackingEntry,Long>{List<TrackingEntry> findByTrackingIdOrderByOccurredAtAsc(String id);List<TrackingEntry> findTop200ByOrderByOccurredAtDesc();}
