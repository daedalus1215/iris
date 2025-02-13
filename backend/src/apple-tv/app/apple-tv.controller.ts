import { Controller, Post, Get } from '@nestjs/common';
import { AppleTvService } from '../domain/apple-tv.service';

@Controller('apple-tv')
export class AppleTvController {
  constructor(private readonly appleTvService: AppleTvService) {}

  @Post('up')
  async up() {
    return this.appleTvService.up();
  }

  @Post('down')
  async down() {
    return this.appleTvService.down();
  }

  @Post('left')
  async left() {
    return this.appleTvService.left();
  }

  @Post('right')
  async right() {
    return this.appleTvService.right();
  }

  @Post('select')
  async select() {
    return this.appleTvService.select();
  }

  @Post('menu')
  async menu() {
    return this.appleTvService.menu();
  }

  @Get('scan')
  async scan() {
    return this.appleTvService.scan();
  }
}