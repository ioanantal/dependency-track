/*
 * This file is part of Dependency-Track.
 *
 * Dependency-Track is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Dependency-Track is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Dependency-Track. If not, see http://www.gnu.org/licenses/.
 */
package org.owasp.dependencytrack.dao;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.criterion.Projection;
import org.owasp.dependencytrack.model.Application;
import org.owasp.dependencytrack.model.ApplicationDependency;
import org.owasp.dependencytrack.model.ApplicationVersion;
import org.owasp.dependencytrack.model.LibraryVersion;
import org.owasp.dependencytrack.util.session.DBSessionTaskReturning;
import org.owasp.dependencytrack.util.session.DBSessionTaskRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Repository("applicationDao")
public class ApplicationDaoImpl extends DBSessionTaskRunner implements ApplicationDao {

    /**
     * Setup logger
     */
    private static final Logger LOGGER = LoggerFactory
            .getLogger(ApplicationDaoImpl.class);

    /**
     * Returns a list of all applications.
     *
     * @return A List of all applications
     */
    @Override
    @SuppressWarnings("unchecked")
    @Transactional
    public List<Application> listApplications() {
        return dbRun(new DBSessionTaskReturning<List<Application>>() {
            @Override
            public List<Application> run(Session session) {
                return session.createQuery("FROM Application order by name asc").list();
            }
        });
    }

    /**
     * Adds an ApplicationVersion to the specified Application.
     *
     * @param application An Application
     * @param version     The ApplicationVersion to add
     */
    @Override
    @Transactional
    public void addApplication(final Application application,
                               final String version) {

        dbRun(new DBSessionTaskReturning<Integer>() {

            @Override
            public Integer run(Session session) {
                session.save(application);

                final ApplicationVersion applicationVersion = new ApplicationVersion();
                applicationVersion.setVersion(version);
                applicationVersion.setApplication(application);

                session.save(applicationVersion);
                return null;
            }

        });
    }

    /**
     * Updates the Application with the specified ID to the name specified.
     *
     * @param id   The ID of the Application
     * @param name The new name of the Application
     */
    @Override
    @Transactional
    public void updateApplication(final int id, final String name) {
        dbRun(new DBSessionTaskReturning<Integer>() {

            @Override
            public Integer run(Session session) {
                final Query query = session
                        .createQuery("update Application set name=:name "
                                + "where id=:id");

                query.setParameter("name", name);
                query.setParameter("id", id);
                query.executeUpdate();
                return null;
            }
        });
    }

    /**
     * Deletes the Application with the specified ID.
     *
     * @param id The ID of the Application to delete
     */
    @Override
    @SuppressWarnings("unchecked")
    @Transactional
    public void deleteApplication(final int id) {
        dbRun(new DBSessionTaskReturning<Integer>() {

            @Override
            public Integer run(Session session) {
                final Application curapp = (Application) session.load(
                        Application.class, id);

                Query query = session.createQuery("from ApplicationVersion "
                        + "where application=:curapp");
                query.setParameter("curapp", curapp);

                final List<ApplicationVersion> applicationVersions = query
                        .list();

                for (ApplicationVersion curver : applicationVersions) {
                    query = session.createQuery("from ApplicationDependency "
                            + "where applicationVersion=:curver");
                    query.setParameter("curver", curver);
                    List<ApplicationDependency> applicationDependency;
                    if (!query.list().isEmpty()) {
                        applicationDependency = query.list();
                        for (ApplicationDependency dependency : applicationDependency) {
                            session.delete(dependency);
                        }
                    }
                    session.delete(curver);
                }
                session.delete(curapp);
                return null;
            }
        });

    }

    /**
     * Returns a Set of Applications that have a dependency on the specified
     * LibraryVersion ID.
     *
     * @param libverid The ID of the LibraryVersion to search on
     * @return A Set of Applications
     */
    @Override
    @SuppressWarnings("unchecked")
    @Transactional
    public Set<Application> searchApplications(final int libverid) {
        return dbRun(new DBSessionTaskReturning<Set<Application>>() {

            @Override
            public Set<Application> run(Session session) {
                Query query = session.createQuery(
                        "FROM LibraryVersion where id=:libverid");
                query.setParameter("libverid", libverid);
                final LibraryVersion libraryVersion = (LibraryVersion) query.list()
                        .get(0);
                query = session.createQuery(
                        "FROM ApplicationDependency where libraryVersion=:libver");
                query.setParameter("libver", libraryVersion);

                final List<ApplicationDependency> apdep = query.list();
                final List<Integer> ids = new ArrayList<>();

                for (ApplicationDependency appdep : apdep) {
                    ids.add(appdep.getApplicationVersion().getId());
                }

                if (!ids.isEmpty()) {
                    query = session
                            .createQuery(
                                    "FROM ApplicationVersion as appver where appver.id in (:appverid)");
                    query.setParameterList("appverid", ids);

                    if (query.list().size() == 0) {
                        return null;
                    }

                    final List<ApplicationVersion> newappver = query.list();
                    final ArrayList<Application> newapp = new ArrayList<>();

                    for (ApplicationVersion version : newappver) {
                        newapp.add(version.getApplication());
                    }
                    return new HashSet<>(newapp);
                }
                return null;
            }
        });
    }

    /**
     * Returns a List of ApplicationVersions that have a dependency on the
     * specified LibraryVersion ID.
     *
     * @param libverid The ID of the LibraryVersion to search on
     * @return A List of ApplicationVersion objects
     */
    @Override
    @SuppressWarnings("unchecked")
    @Transactional
    public List<ApplicationVersion> searchApplicationsVersion(final int libverid) {
        return dbRun(new DBSessionTaskReturning<List<ApplicationVersion>>() {
            @Override
            public List<ApplicationVersion> run(Session session) {
                Query query = session.createQuery(
                        "FROM LibraryVersion where id=:libverid");
                query.setParameter("libverid", libverid);
                final LibraryVersion libraryVersion = (LibraryVersion) query.list()
                        .get(0);
                query = session.createQuery(
                        "FROM ApplicationDependency where libraryVersion=:libver");
                query.setParameter("libver", libraryVersion);

                final List<ApplicationDependency> apdep = query.list();
                final List<Integer> ids = new ArrayList<>();

                for (ApplicationDependency appdep : apdep) {
                    ids.add(appdep.getApplicationVersion().getId());
                }
                if (!ids.isEmpty()) {
                    query = session
                            .createQuery(
                                    " FROM ApplicationVersion as appver where appver.id in (:appverid)");
                    query.setParameterList("appverid", ids);

                    return query.list();
                }

                return null;
            }
        });

    }

    /**
     * Returns a Set of Applications that have a dependency on the specified
     * Library ID.
     *
     * @param libid The ID of the Library to search on
     * @return A Set of Application objects
     */
    @Override
    @SuppressWarnings("unchecked")
    @Transactional
    public Set<Application> searchAllApplications(final int libid) {

        return dbRun(new DBSessionTaskReturning<Set<Application>>() {
            @Override
            public Set<Application> run(Session session) {
                Query query = session.createQuery(
                        "select lib.versions FROM Library as lib where lib.id=:libid");
                query.setParameter("libid", libid);
                final List<LibraryVersion> libver = query.list();
                query = session
                        .createQuery(
                                "FROM ApplicationDependency as appdep where appdep.libraryVersion in (:libver)");
                query.setParameterList("libver", libver);

                final List<ApplicationDependency> apdep = query.list();
                final List<Integer> ids = new ArrayList<>();

                for (ApplicationDependency appdep : apdep) {
                    ids.add(appdep.getApplicationVersion().getId());
                }
                if (!ids.isEmpty()) {

                    query = session
                            .createQuery(
                                    "FROM ApplicationVersion as appver where appver.id in (:appverid)");
                    query.setParameterList("appverid", ids);

                    final List<ApplicationVersion> newappver = query.list();
                    final ArrayList<Application> newapp = new ArrayList<>();

                    for (ApplicationVersion version : newappver) {
                        newapp.add(version.getApplication());
                    }
                    return new HashSet<>(newapp);
                }
                return null;
            }
        });
    }

    /**
     * Returns a List of ApplicationVersions that have a dependency on the
     * specified Library ID.
     *
     * @param libid The ID of the Library to search on
     * @return a List of ApplicationVersion objects
     */
    @Override
    @SuppressWarnings("unchecked")
    @Transactional
    public List<ApplicationVersion> searchAllApplicationsVersions(final int libid) {

        return dbRun(new DBSessionTaskReturning<List<ApplicationVersion> >() {
            @Override
            public List<ApplicationVersion> run(Session session) {
                Query query = session.createQuery(
                        "select lib.versions FROM Library as lib where lib.id=:libid");
                query.setParameter("libid", libid);
                final List<LibraryVersion> libver = query.list();
                query = session
                        .createQuery(
                                "FROM ApplicationDependency as appdep where appdep.libraryVersion in (:libver)");
                query.setParameterList("libver", libver);

                final List<ApplicationDependency> apdep = query.list();
                final List<Integer> ids = new ArrayList<>();

                for (ApplicationDependency appdep : apdep) {
                    ids.add(appdep.getApplicationVersion().getId());
                }
                if (!ids.isEmpty()) {
                    query = session
                            .createQuery(
                                    "FROM ApplicationVersion as appver where appver.id in (:appverid)");
                    query.setParameterList("appverid", ids);
                    return query.list();
                }
                return null;
            }
        });
    }

    /**
     * Returns a List of Application that have a library of this vendor.
     *
     * @param vendorID The ID of the Library to search on
     * @return a List of ApplicationVersion objects
     */
    @Override
    @SuppressWarnings("unchecked")
    @Transactional
    public Set<Application> coarseSearchApplications(final int vendorID) {
        return dbRun(new DBSessionTaskReturning<Set<Application> >() {
            @Override
            public Set<Application> run(Session session) {
                Query query = session
                        .createQuery(
                                "select lib.versions FROM Library as lib where lib.libraryVendor.id=:vendorID");
                query.setParameter("vendorID", vendorID);

                final List<LibraryVersion> libver = query.list();

                query = session
                        .createQuery(
                                "FROM ApplicationDependency as appdep where appdep.libraryVersion in (:libver)");
                query.setParameterList("libver", libver);

                final List<ApplicationDependency> apdep = query.list();
                final List<Integer> ids = new ArrayList<>();

                for (ApplicationDependency appdep : apdep) {
                    ids.add(appdep.getApplicationVersion().getId());
                }
                if (!ids.isEmpty()) {

                    query = session
                            .createQuery(
                                    "FROM ApplicationVersion as appver where appver.id in (:appverid)");
                    query.setParameterList("appverid", ids);

                    final List<ApplicationVersion> newappver = query.list();
                    final ArrayList<Application> newapp = new ArrayList<>();

                    for (ApplicationVersion version : newappver) {
                        newapp.add(version.getApplication());
                    }
                    return new HashSet<>(newapp);
                }
                return null;


            }
        });

    }

    /**
     * Returns a List of ApplicationVersions that have a dependency on the
     * specified Library Vendor.
     *
     * @param vendorID The ID of the Vendor to search on
     * @return a List of ApplicationVersion objects
     */
    @Override
    @SuppressWarnings("unchecked")
    @Transactional
    public List<ApplicationVersion> coarseSearchApplicationVersions(final int vendorID) {

        return dbRun(new DBSessionTaskReturning<List<ApplicationVersion>>() {
            @Override
            public List<ApplicationVersion> run(Session session) {
                Query query = session
                        .createQuery(
                                "select lib.versions FROM Library as lib where lib.libraryVendor.id=:vendorID");
                query.setParameter("vendorID", vendorID);

                final List<LibraryVersion> libver = query.list();

                query = session
                        .createQuery(
                                "FROM ApplicationDependency as appdep where appdep.libraryVersion in (:libver)");
                query.setParameterList("libver", libver);

                final List<ApplicationDependency> apdep = query.list();
                final List<Integer> ids = new ArrayList<>();

                for (ApplicationDependency appdep : apdep) {
                    ids.add(appdep.getApplicationVersion().getId());
                }
                if (!ids.isEmpty()) {
                    query = session
                            .createQuery(
                                    "FROM ApplicationVersion as appver where appver.id in (:appverid)");
                    query.setParameterList("appverid", ids);
                    return query.list();
                }
                return null;


            }
        });
    }
}