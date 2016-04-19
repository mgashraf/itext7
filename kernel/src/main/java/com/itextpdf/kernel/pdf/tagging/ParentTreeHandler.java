/*
    $Id$

    This file is part of the iText (R) project.
    Copyright (c) 1998-2016 iText Group NV
    Authors: Bruno Lowagie, Paulo Soares, et al.

    This program is free software; you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License version 3
    as published by the Free Software Foundation with the addition of the
    following permission added to Section 15 as permitted in Section 7(a):
    FOR ANY PART OF THE COVERED WORK IN WHICH THE COPYRIGHT IS OWNED BY
    ITEXT GROUP. ITEXT GROUP DISCLAIMS THE WARRANTY OF NON INFRINGEMENT
    OF THIRD PARTY RIGHTS

    This program is distributed in the hope that it will be useful, but
    WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
    or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License
    along with this program; if not, see http://www.gnu.org/licenses or write to
    the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
    Boston, MA, 02110-1301 USA, or download the license from the following URL:
    http://itextpdf.com/terms-of-use/

    The interactive user interfaces in modified source and object code versions
    of this program must display Appropriate Legal Notices, as required under
    Section 5 of the GNU Affero General Public License.

    In accordance with Section 7(b) of the GNU Affero General Public License,
    a covered work must retain the producer line in every PDF that is created
    or manipulated using iText.

    You can be released from the requirements of the license by purchasing
    a commercial license. Buying such a license is mandatory as soon as you
    develop commercial activities involving the iText software without
    disclosing the source code of your own applications.
    These activities include: offering paid services to customers as an ASP,
    serving PDFs on the fly in a web application, shipping iText with a closed
    source product.

    For more information, please contact iText Software Corp. at this
    address: sales@itextpdf.com
 */
package com.itextpdf.kernel.pdf.tagging;

import com.itextpdf.kernel.PdfException;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfIndirectReference;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNull;
import com.itextpdf.kernel.pdf.PdfNumTree;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Internal helper class which is used to effectively build parent tree and also find marked content references:
 * for specified page, by MCID or by struct parent index.
 */
class ParentTreeHandler implements Serializable {

    private static final long serialVersionUID = 1593883864288316473L;

    private PdfStructTreeRoot structTreeRoot;

    /**
     * Represents parentTree in structTreeRoot. It contains only those entries that belong to the already flushed pages.
     */
    private PdfNumTree parentTree;

    /**
     * Contains marked content references for every page.
     * If new mcrs are added to the tag structure, these new mcrs are also added to this map. So for each adding or
     * removing mcr, register/unregister calls must be made (this is done automatically if addKid or removeKid methods
     * of PdfStructElem are used).
     *
     * Keys in this map are page references, values - a map which contains all mcrs that belong to the given page.
     * This inner map of mcrs is of following structure:
     *      * for McrDictionary and McrNumber values the keys are their MCIDs;
     *      * for ObjRef values the keys are struct parent indexes, but with one trick. Struct parent indexes and MCIDs have the
     *        same value domains: the increasing numbers starting from zero. So, in order to store them in one map, for
     *        struct parent indexes simple transformation is applied via {@code #structParentIndexIntoKey}
     *        and {@code #keyIntoStructParentIndex}. With this we simply store struct parent indexes as negative numbers.
     */
    private Map<PdfIndirectReference, TreeMap<Integer, PdfMcr>> pageToPageMcrs;

    /**
     * Init ParentTreeHandler. On init the parent tree is read and stored in this instance.
     */
    ParentTreeHandler(PdfStructTreeRoot structTreeRoot) {
        this.structTreeRoot = structTreeRoot;
        parentTree = new PdfNumTree(structTreeRoot.getDocument().getCatalog(), PdfName.ParentTree);
        registerAllMcrs();
    }

    /**
     * Gets a list of marked content references on page.
     */
    public Map<Integer, PdfMcr> getPageMarkedContentReferences(PdfPage page) {
        return pageToPageMcrs.get(page.getPdfObject().getIndirectReference());
    }

    public PdfMcr findMcrByMcid(PdfDictionary pageDict, int mcid) {
        Map<Integer, PdfMcr> pageMcrs = pageToPageMcrs.get(pageDict.getIndirectReference());
        return pageMcrs != null ? pageMcrs.get(mcid) : null;
    }

    public PdfObjRef findObjRefByStructParentIndex(PdfDictionary pageDict, int structParentIndex) {
        Map<Integer, PdfMcr> pageMcrs = pageToPageMcrs.get(pageDict.getIndirectReference());
        return pageMcrs != null ? (PdfObjRef) pageMcrs.get(structParentIndexIntoKey(structParentIndex)) : null;
    }

    public int getNextMcidForPage(PdfPage page) {
        TreeMap<Integer, PdfMcr> pageMcrs = pageToPageMcrs.get(page.getPdfObject().getIndirectReference());
        if (pageMcrs == null || pageMcrs.isEmpty()) {
            return 0;
        } else {
            int lastKey = pageMcrs.lastKey();
            if (lastKey < 0) {
                return 0;
            }
            return lastKey + 1;
        }
    }

    /**
     * Creates and flushes parent tree entry for the page.
     * Effectively this means that new content mustn't be added to the page.
     *
     * @param page {@link PdfPage} for which to create parent tree entry. Typically this page is flushed after this call.
     */
    public void createParentTreeEntryForPage(PdfPage page) {
        Map<Integer, PdfMcr> mcrs = getPageMarkedContentReferences(page);
        if (mcrs == null) {
            return;
        }
        pageToPageMcrs.remove(page.getPdfObject().getIndirectReference());
        updateStructParentTreeEntries(page.getStructParentIndex(), mcrs);
    }

    public PdfDictionary buildParentTree() {
        return parentTree.buildTree().makeIndirect(structTreeRoot.getDocument());
    }

    public void registerMcr(PdfMcr mcr) {
        TreeMap<Integer, PdfMcr> pageMcrs = pageToPageMcrs.get(mcr.getPageObject().getIndirectReference());
        if (pageMcrs == null) {
            pageMcrs = new TreeMap<>();
            pageToPageMcrs.put(mcr.getPageObject().getIndirectReference(), pageMcrs);
        }
        if (mcr instanceof PdfObjRef) {
            PdfDictionary obj = ((PdfDictionary) mcr.getPdfObject()).getAsDictionary(PdfName.Obj);
            if (obj == null || obj.isFlushed()) {
                throw new PdfException(PdfException.WhenAddingObjectReferenceToTheTagTreeItMustBeConnectedToNotFlushedObject);
            }
            PdfNumber n = obj.getAsNumber(PdfName.StructParent);
            if (n != null) {
                pageMcrs.put(structParentIndexIntoKey(n.intValue()), mcr);
            } else {
                throw new PdfException(PdfException.StructParentIndexNotFoundInTaggedObject);
            }
        } else {
            pageMcrs.put(mcr.getMcid(), mcr);
        }
    }

    public void unregisterMcr(PdfMcr mcrToUnregister) {
        PdfDictionary pageDict = mcrToUnregister.getPageObject();
        if (pageDict.isFlushed()) {
            throw new PdfException(PdfException.CannotRemoveMarkedContentReferenceBecauseItsPageWasAlreadyFlushed);
        }
        Map<Integer, PdfMcr> pageMcrs = pageToPageMcrs.get(pageDict.getIndirectReference());
        if (pageMcrs != null) {
            if (mcrToUnregister instanceof PdfObjRef) {

                PdfDictionary obj = ((PdfDictionary) mcrToUnregister.getPdfObject()).getAsDictionary(PdfName.Obj);
                if (obj != null && !obj.isFlushed()) {
                    PdfNumber n = obj.getAsNumber(PdfName.StructParent);
                    if (n != null) {
                        pageMcrs.remove(structParentIndexIntoKey(n.intValue()));
                        return;
                    }
                }

                Integer keyToRemove = null;
                for (Map.Entry<Integer, PdfMcr> entry : pageMcrs.entrySet()) {
                    if (entry.getValue().getPdfObject() == mcrToUnregister.getPdfObject()) {
                        keyToRemove = entry.getKey();
                        break;
                    }
                }
                if (keyToRemove != null) {
                    pageMcrs.remove(keyToRemove);
                }
            } else {
                pageMcrs.remove(mcrToUnregister.getMcid());
            }
        }
    }

    private static Integer structParentIndexIntoKey(int structParentIndex) {
        return -structParentIndex - 1;
    }

    private static int keyIntoStructParentIndex(Integer key) {
        return -key - 1;
    }

    private void registerAllMcrs() {
        pageToPageMcrs = new HashMap<>();
        // we create new number tree and not using parentTree, because we want parentTree to be empty
        Map<Integer, PdfObject> parentTreeEntries = new PdfNumTree(structTreeRoot.getDocument().getCatalog(), PdfName.ParentTree).getNumbers();
        Set<PdfStructElem> mcrParents = new HashSet<>();
        int maxStructParentIndex = -1;
        for (Map.Entry<Integer, PdfObject> entry : parentTreeEntries.entrySet()) {
            if (entry.getKey() > maxStructParentIndex) {
                maxStructParentIndex = entry.getKey();
            }

            PdfObject entryValue = entry.getValue();
            if (entryValue.isDictionary()) {
                mcrParents.add(new PdfStructElem((PdfDictionary) entryValue));
            } else if (entryValue.isArray()) {
                PdfArray parentsArray = (PdfArray) entryValue;
                for (int i = 0; i < parentsArray.size(); ++i) {
                    PdfDictionary parent = parentsArray.getAsDictionary(i);
                    if (parent != null) {
                        mcrParents.add(new PdfStructElem(parent));
                    }
                }
            }
        }
        structTreeRoot.getPdfObject().put(PdfName.ParentTreeNextKey, new PdfNumber(maxStructParentIndex + 1));

        for (PdfStructElem mcrParent : mcrParents) {
            for (IPdfStructElem kid : mcrParent.getKids()) {
                if (kid instanceof PdfMcr) {
                    registerMcr((PdfMcr) kid);
                }
            }
        }
    }

    private void updateStructParentTreeEntries(Integer pageStructParentIndex, Map<Integer, PdfMcr> mcrs) {
        // element indexes in parentsOfPageMcrs shall be the same as mcid of one of their kids.
        // See "Finding Structure Elements from Content Items" in pdf spec.
        PdfArray parentsOfPageMcrs = new PdfArray();
        int currentMcid = 0;
        for (Map.Entry<Integer, PdfMcr> entry : mcrs.entrySet()) {
            PdfMcr mcr = entry.getValue();
            if (mcr instanceof PdfObjRef) {
                Integer structParent = keyIntoStructParentIndex(entry.getKey());
                parentTree.addEntry(structParent, ((PdfStructElem) mcr.getParent()).getPdfObject());
            } else {
                // if for some reason some mcr where not registered or don't exist, we ensure that the rest
                // of the parent objects were placed at correct index
                while (currentMcid++ < mcr.getMcid()) {
                    parentsOfPageMcrs.add(PdfNull.PDF_NULL);
                }
                parentsOfPageMcrs.add(((PdfStructElem) mcr.getParent()).getPdfObject());
            }
        }


        if (!parentsOfPageMcrs.isEmpty()) {
            parentsOfPageMcrs.makeIndirect(structTreeRoot.getDocument());
            parentTree.addEntry(pageStructParentIndex, parentsOfPageMcrs);
            parentsOfPageMcrs.flush();
        }
    }
}