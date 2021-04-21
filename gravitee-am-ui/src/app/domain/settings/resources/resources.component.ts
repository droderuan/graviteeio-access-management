/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import {Component, OnInit} from '@angular/core';
import {ActivatedRoute} from '@angular/router';

@Component({
  selector: 'app-domain-resources',
  templateUrl: './resources.component.html',
  styleUrls: ['./resources.component.scss']
})
export class DomainSettingsResourcesComponent implements OnInit {
  private resourceTypes: any = {
    'twilio-verify-am-resource' : 'Twilio Verify',
    'smtp-am-resource' : 'SMTP'
  };
  resources: any[];
  domainId: any;

  constructor(private route: ActivatedRoute) {
  }

  ngOnInit() {
    this.domainId = this.route.snapshot.data['domain']?.id;
    this.resources = this.route.snapshot.data['resources'];
  }

  isEmpty() {
    return !this.resources || this.resources.length === 0;
  }

  displayType(type) {
    if (this.resourceTypes[type]) {
      return this.resourceTypes[type];
    }
    return type;
  }
}